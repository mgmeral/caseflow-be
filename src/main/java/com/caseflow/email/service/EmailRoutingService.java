package com.caseflow.email.service;

import com.caseflow.common.domain.UnknownSenderPolicy;
import com.caseflow.customer.domain.CustomerEmailRoutingRule;
import com.caseflow.customer.domain.CustomerEmailSettings;
import com.caseflow.customer.domain.SenderMatchType;
import com.caseflow.customer.repository.CustomerEmailRoutingRuleRepository;
import com.caseflow.customer.repository.CustomerEmailSettingsRepository;
import com.caseflow.email.domain.EmailIngressEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Deterministic customer-based routing algorithm for inbound emails.
 *
 * <p>Routing is CUSTOMER-based. Contact records are NOT consulted.
 * Incoming sender email/domain is matched against {@code CustomerEmailRoutingRule} entries.
 *
 * <p>Precedence (first match wins):
 * <ol>
 *   <li>Thread resolution via In-Reply-To / References headers → LINK_TO_TICKET</li>
 *   <li>Exact-email routing rule (ordered by priority ASC, lower = higher priority)</li>
 *   <li>Domain-suffix routing rule (ordered by priority ASC)</li>
 *   <li>Unknown sender — apply mailbox/global UnknownSenderPolicy</li>
 * </ol>
 *
 * <p>If two rules with equal priority both match (ambiguous), the first one found wins
 * (stable sort by priority). Ambiguous conflicts should be resolved by the operator
 * via the routing-rule management API.
 */
@Service
public class EmailRoutingService {

    private static final Logger log = LoggerFactory.getLogger(EmailRoutingService.class);

    private final CustomerEmailSettingsRepository settingsRepository;
    private final CustomerEmailRoutingRuleRepository routingRuleRepository;
    private final EmailThreadingService threadingService;

    public EmailRoutingService(CustomerEmailSettingsRepository settingsRepository,
                               CustomerEmailRoutingRuleRepository routingRuleRepository,
                               EmailThreadingService threadingService) {
        this.settingsRepository = settingsRepository;
        this.routingRuleRepository = routingRuleRepository;
        this.threadingService = threadingService;
    }

    @Transactional(readOnly = true)
    public RoutingResult route(EmailIngressEvent event) {
        String from = normalizeEmail(event.getRawFrom());
        log.debug("Routing inbound email — messageId: '{}', from: '{}'", event.getMessageId(), from);

        // 1. Thread resolution — link to existing ticket if headers resolve one
        Optional<Long> existingTicketId = threadingService.resolveTicketId(
                event.getInReplyTo(),
                event.getReferencesList());
        if (existingTicketId.isPresent()) {
            log.info("Routing via thread — ticketId: {}", existingTicketId.get());
            return RoutingResult.linkToTicket(null, existingTicketId.get());
        }

        String domain = extractDomain(from);

        // 2 & 3: Check routing rules (all customers, ordered by priority)
        List<CustomerEmailRoutingRule> allActiveRules = routingRuleRepository.findAll().stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .sorted((a, b) -> Integer.compare(a.getPriority(), b.getPriority()))
                .toList();

        // Exact email rules first (higher specificity than domain)
        for (CustomerEmailRoutingRule rule : allActiveRules) {
            if (rule.getSenderMatchType() == SenderMatchType.EXACT_EMAIL
                    && from.equalsIgnoreCase(rule.getMatchValue())) {
                log.info("Routing via exact-email rule — customerId: {}", rule.getCustomerId());
                return RoutingResult.createTicket(rule.getCustomerId());
            }
        }

        // Domain rules — normalize stored value (strip leading '@' if present)
        for (CustomerEmailRoutingRule rule : allActiveRules) {
            if (rule.getSenderMatchType() == SenderMatchType.DOMAIN
                    && domain != null) {
                String ruleValue = rule.getMatchValue().replaceFirst("^@", "");
                if (domain.equalsIgnoreCase(ruleValue)) {
                    log.info("Routing via domain rule — customerId: {}", rule.getCustomerId());
                    return RoutingResult.createTicket(rule.getCustomerId());
                }
            }
        }

        // 4. Unknown sender — apply global/mailbox policy
        log.info("No routing match for from: '{}' — applying unknown sender policy", from);
        return applyUnknownSenderPolicy(from);
    }

    private RoutingResult applyUnknownSenderPolicy(String from) {
        // Use the first active global setting, or default to MANUAL_REVIEW (→ QUARANTINE)
        UnknownSenderPolicy policy = settingsRepository.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                .map(CustomerEmailSettings::getUnknownSenderPolicy)
                .findFirst()
                .orElse(UnknownSenderPolicy.MANUAL_REVIEW);

        return switch (policy) {
            case CREATE_UNMATCHED_TICKET -> RoutingResult.createTicket(null);
            case IGNORE -> RoutingResult.ignore();
            case REJECT -> RoutingResult.reject();
            default -> RoutingResult.quarantine("Unknown sender: " + from);
        };
    }

    private String normalizeEmail(String raw) {
        if (raw == null) return "";
        // Strip display name: "John Doe <john@example.com>" → "john@example.com"
        int start = raw.lastIndexOf('<');
        int end = raw.lastIndexOf('>');
        if (start >= 0 && end > start) {
            return raw.substring(start + 1, end).trim().toLowerCase();
        }
        return raw.trim().toLowerCase();
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
