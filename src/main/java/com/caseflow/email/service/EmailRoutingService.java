package com.caseflow.email.service;

import com.caseflow.common.domain.UnknownSenderPolicy;
import com.caseflow.customer.domain.CustomerEmailRoutingRule;
import com.caseflow.customer.domain.CustomerEmailSettings;
import com.caseflow.customer.domain.SenderMatchType;
import com.caseflow.customer.repository.CustomerEmailRoutingRuleRepository;
import com.caseflow.customer.repository.CustomerEmailSettingsRepository;
import com.caseflow.email.domain.EmailIngressEvent;
import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.repository.EmailMailboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Deterministic customer-based routing algorithm for inbound emails.
 *
 * <p>Routing is CUSTOMER-based. Contact records are NOT consulted.
 * Incoming sender email/domain is matched against {@code CustomerEmailRoutingRule} entries.
 *
 * <h2>Precedence (in order)</h2>
 * <ol>
 *   <li>Thread resolution via In-Reply-To / References headers → LINK_TO_TICKET</li>
 *   <li>Exact-email routing rules (highest specificity, ordered by priority ASC)</li>
 *   <li>Domain-suffix routing rules (ordered by priority ASC, then domain length DESC for tie-breaking)</li>
 *   <li>Unknown sender — apply mailbox/global UnknownSenderPolicy</li>
 * </ol>
 *
 * <h2>Precheck path</h2>
 * {@link #routeHeaders(String, String, List, Long)} accepts only parsed header fields and does not
 * require a persisted {@link EmailIngressEvent}. Use this in the IMAP poller before body parsing
 * and attachment upload. {@link #route(EmailIngressEvent)} delegates to the same logic.
 *
 * <h2>Domain matching</h2>
 * For a domain rule with {@code matchValue = "bigcorp.com"}:
 * <ul>
 *   <li>Exact domain match: {@code @bigcorp.com} always matches.</li>
 *   <li>Subdomain match (when {@code allowSubdomains = true}): {@code @sub.bigcorp.com} also matches.</li>
 * </ul>
 *
 * <h2>Ambiguity detection</h2>
 * If two domain rules with equal priority AND equal domain-specificity (same length) both match,
 * the email is quarantined. If two exact-email rules for different customers both match the same
 * sender, the email is also quarantined. Operators must resolve conflicts by adjusting priorities.
 *
 * <h2>Unknown sender policy resolution order</h2>
 * <ol>
 *   <li>Mailbox-level {@code unknownSenderPolicy} if set.</li>
 *   <li>First active global {@code CustomerEmailSettings.unknownSenderPolicy} (sorted by id).</li>
 *   <li>Default: MANUAL_REVIEW → quarantine.</li>
 * </ol>
 */
@Service
public class EmailRoutingService {

    private static final Logger log = LoggerFactory.getLogger(EmailRoutingService.class);

    private final CustomerEmailSettingsRepository settingsRepository;
    private final CustomerEmailRoutingRuleRepository routingRuleRepository;
    private final EmailMailboxRepository mailboxRepository;
    private final EmailThreadingService threadingService;

    public EmailRoutingService(CustomerEmailSettingsRepository settingsRepository,
                               CustomerEmailRoutingRuleRepository routingRuleRepository,
                               EmailMailboxRepository mailboxRepository,
                               EmailThreadingService threadingService) {
        this.settingsRepository = settingsRepository;
        this.routingRuleRepository = routingRuleRepository;
        this.mailboxRepository = mailboxRepository;
        this.threadingService = threadingService;
    }

    /**
     * Routes an inbound email based on headers only (no persisted entity required).
     * Safe to call before body parsing or attachment upload.
     */
    @Transactional(readOnly = true)
    public RoutingResult routeHeaders(String rawFrom, String inReplyTo,
                                      List<String> references, Long mailboxId) {
        String from = normalizeEmail(rawFrom);
        log.debug("ROUTING_CHECK from: '{}'", from);

        // 1. Thread resolution
        Optional<Long> existingTicketId = threadingService.resolveTicketId(inReplyTo, references);
        if (existingTicketId.isPresent()) {
            log.info("ROUTING_MATCH_THREAD — ticketId: {}", existingTicketId.get());
            return RoutingResult.linkToTicket(null, existingTicketId.get());
        }

        String domain = extractDomain(from);

        List<CustomerEmailRoutingRule> allActiveRules = routingRuleRepository.findAll().stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .sorted(Comparator.comparingInt(CustomerEmailRoutingRule::getPriority))
                .toList();

        // 2. Exact email rules (always beat domain rules)
        List<CustomerEmailRoutingRule> exactMatches = allActiveRules.stream()
                .filter(r -> r.getSenderMatchType() == SenderMatchType.EXACT_EMAIL
                        && from.equalsIgnoreCase(normalizeMatchValue(r.getMatchValue())))
                .toList();

        if (!exactMatches.isEmpty()) {
            // Ambiguity: multiple exact rules pointing to different customers → quarantine
            boolean ambiguous = exactMatches.stream()
                    .map(CustomerEmailRoutingRule::getCustomerId)
                    .distinct()
                    .count() > 1;
            if (ambiguous) {
                log.warn("ROUTING_AMBIGUOUS — {} exact-email rules match from '{}' for different customers — quarantining",
                        exactMatches.size(), from);
                return RoutingResult.quarantine(
                        "Ambiguous routing: multiple exact-email rules from different customers match " + from
                                + ". Operator must resolve priority conflict.");
            }
            CustomerEmailRoutingRule winner = exactMatches.get(0);
            log.info("ROUTING_MATCH_EXACT — customerId: {}, ruleId: {}", winner.getCustomerId(), winner.getId());
            return RoutingResult.createTicket(winner.getCustomerId());
        }

        // 3. Domain rules
        if (domain != null) {
            List<CustomerEmailRoutingRule> domainMatches = allActiveRules.stream()
                    .filter(r -> r.getSenderMatchType() == SenderMatchType.DOMAIN)
                    .filter(r -> domainMatches(domain, r))
                    .toList();

            if (!domainMatches.isEmpty()) {
                return selectDomainWinner(domainMatches, from);
            }
        }

        // 4. Unknown sender
        log.info("ROUTING_NO_MATCH — from: '{}' — applying unknown sender policy", from);
        return applyUnknownSenderPolicy(from, mailboxId);
    }

    /**
     * Routes a persisted inbound email event. Delegates to {@link #routeHeaders}.
     */
    @Transactional(readOnly = true)
    public RoutingResult route(EmailIngressEvent event) {
        return routeHeaders(event.getRawFrom(), event.getInReplyTo(),
                event.getReferencesList(), event.getMailboxId());
    }

    // ── Domain matching ───────────────────────────────────────────────────────

    private boolean domainMatches(String senderDomain, CustomerEmailRoutingRule rule) {
        String ruleValue = normalizeMatchValue(rule.getMatchValue()).replaceFirst("^@", "");
        String sender = senderDomain.toLowerCase();

        if (sender.equals(ruleValue)) {
            return true;
        }
        if (Boolean.TRUE.equals(rule.getAllowSubdomains())) {
            return sender.endsWith("." + ruleValue);
        }
        return false;
    }

    /**
     * Selects the winning domain rule.
     * Tiebreaking: lower priority number wins, then longer domain value (more specific).
     * Equal priority AND equal domain length from different customers → quarantine (ambiguous).
     */
    private RoutingResult selectDomainWinner(List<CustomerEmailRoutingRule> matches, String from) {
        List<CustomerEmailRoutingRule> ordered = matches.stream()
                .sorted(Comparator
                        .<CustomerEmailRoutingRule, Integer>comparing(CustomerEmailRoutingRule::getPriority)
                        .thenComparing(Comparator
                                .<CustomerEmailRoutingRule, Integer>comparing(r ->
                                        normalizeMatchValue(r.getMatchValue()).replaceFirst("^@", "").length())
                                .reversed()))
                .toList();

        CustomerEmailRoutingRule best = ordered.get(0);
        int bestDomainLen = normalizeMatchValue(best.getMatchValue()).replaceFirst("^@", "").length();

        // Ambiguous if multiple rules at same priority + same domain length point to different customers
        long ambiguousCount = ordered.stream()
                .filter(r -> r.getPriority().equals(best.getPriority())
                        && normalizeMatchValue(r.getMatchValue()).replaceFirst("^@", "").length() == bestDomainLen
                        && !r.getCustomerId().equals(best.getCustomerId()))
                .count();

        if (ambiguousCount > 0) {
            log.warn("ROUTING_AMBIGUOUS — {} equal-weight domain rules match from '{}' — quarantining",
                    ambiguousCount + 1, from);
            return RoutingResult.quarantine(
                    "Ambiguous routing: equal-priority domain rules match " + from
                            + ". Operator must resolve priority conflict.");
        }

        log.info("ROUTING_MATCH_DOMAIN — customerId: {}, ruleId: {}, matchValue: '{}'",
                best.getCustomerId(), best.getId(), best.getMatchValue());
        return RoutingResult.createTicket(best.getCustomerId());
    }

    // ── Unknown sender policy ─────────────────────────────────────────────────

    private RoutingResult applyUnknownSenderPolicy(String from, Long mailboxId) {
        if (mailboxId != null) {
            Optional<UnknownSenderPolicy> mailboxPolicy = mailboxRepository.findById(mailboxId)
                    .map(EmailMailbox::getUnknownSenderPolicy)
                    .filter(p -> p != null);
            if (mailboxPolicy.isPresent()) {
                log.debug("ROUTING_NO_MATCH applying mailbox-level policy: {}", mailboxPolicy.get());
                return applyPolicy(mailboxPolicy.get(), from);
            }
        }

        Optional<UnknownSenderPolicy> globalPolicy = settingsRepository.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                .sorted(Comparator.comparing(CustomerEmailSettings::getId))
                .map(CustomerEmailSettings::getUnknownSenderPolicy)
                .findFirst();

        UnknownSenderPolicy policy = globalPolicy.orElse(UnknownSenderPolicy.MANUAL_REVIEW);
        log.debug("ROUTING_NO_MATCH applying global policy: {}", policy);
        return applyPolicy(policy, from);
    }

    private RoutingResult applyPolicy(UnknownSenderPolicy policy, String from) {
        return switch (policy) {
            case IGNORE -> RoutingResult.ignore();
            case REJECT -> RoutingResult.reject();
            default -> RoutingResult.quarantine("Unknown sender: " + from);
        };
    }

    // ── Email normalization ───────────────────────────────────────────────────

    private String normalizeEmail(String raw) {
        if (raw == null) return "";
        int start = raw.lastIndexOf('<');
        int end = raw.lastIndexOf('>');
        if (start >= 0 && end > start) {
            return raw.substring(start + 1, end).trim().toLowerCase();
        }
        return raw.trim().toLowerCase();
    }

    /** Trims and lowercases a match value from a routing rule. */
    private String normalizeMatchValue(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase();
    }

    private String extractDomain(String email) {
        if (email == null) return null;
        int at = email.lastIndexOf('@');
        if (at >= 0 && at < email.length() - 1) {
            return email.substring(at + 1);
        }
        return null;
    }
}
