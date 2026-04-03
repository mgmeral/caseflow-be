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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests for the customer-based routing algorithm.
 *
 * Routing is contact-free — no ContactRepository.
 * Precedence: thread → exact-email rule → domain rule → policy.
 */
@ExtendWith(MockitoExtension.class)
class EmailRoutingServiceTest {

    @Mock private CustomerEmailSettingsRepository settingsRepository;
    @Mock private CustomerEmailRoutingRuleRepository routingRuleRepository;
    @Mock private EmailMailboxRepository mailboxRepository;
    @Mock private EmailThreadingService threadingService;

    @InjectMocks
    private EmailRoutingService routingService;

    @BeforeEach
    void defaultStubs() {
        lenient().when(routingRuleRepository.findAll()).thenReturn(List.of());
        lenient().when(settingsRepository.findAll()).thenReturn(List.of());
        lenient().when(threadingService.resolveTicketId(any(), any())).thenReturn(Optional.empty());
        lenient().when(mailboxRepository.findById(any())).thenReturn(Optional.empty());
    }

    // ── Thread resolution → LINK_TO_TICKET ───────────────────────────────────

    @Test
    void route_linksToTicket_whenThreadResolvesViaInReplyTo() {
        when(threadingService.resolveTicketId(any(), any())).thenReturn(Optional.of(77L));
        EmailIngressEvent event = eventWithInReplyTo("reply@example.com", "<parent@example.com>");

        RoutingResult result = routingService.route(event);

        assertEquals(RoutingResult.Action.LINK_TO_TICKET, result.action());
        assertEquals(77L, result.ticketId());
    }

    // ── Exact-email routing rule → CREATE_TICKET ──────────────────────────────

    @Test
    void route_createsTicket_forExactEmailRule() {
        CustomerEmailRoutingRule rule = rule(5L, SenderMatchType.EXACT_EMAIL, "support@bigcorp.com", 10);
        when(routingRuleRepository.findAll()).thenReturn(List.of(rule));

        RoutingResult result = routingService.route(event("support@bigcorp.com"));

        assertEquals(RoutingResult.Action.CREATE_TICKET, result.action());
        assertEquals(5L, result.customerId());
    }

    @Test
    void route_exactEmailRule_isCaseInsensitive() {
        CustomerEmailRoutingRule rule = rule(5L, SenderMatchType.EXACT_EMAIL, "SUPPORT@BIGCORP.COM", 10);
        when(routingRuleRepository.findAll()).thenReturn(List.of(rule));

        RoutingResult result = routingService.route(event("support@bigcorp.com"));

        assertEquals(RoutingResult.Action.CREATE_TICKET, result.action());
        assertEquals(5L, result.customerId());
    }

    // ── Domain routing rule → CREATE_TICKET ───────────────────────────────────

    @Test
    void route_createsTicket_forDomainRule() {
        CustomerEmailRoutingRule rule = rule(7L, SenderMatchType.DOMAIN, "bigcorp.com", 20);
        when(routingRuleRepository.findAll()).thenReturn(List.of(rule));

        RoutingResult result = routingService.route(event("any.user@bigcorp.com"));

        assertEquals(RoutingResult.Action.CREATE_TICKET, result.action());
        assertEquals(7L, result.customerId());
    }

    @Test
    void route_domainRule_handlesLeadingAtSign() {
        CustomerEmailRoutingRule rule = rule(7L, SenderMatchType.DOMAIN, "@bigcorp.com", 20);
        when(routingRuleRepository.findAll()).thenReturn(List.of(rule));

        RoutingResult result = routingService.route(event("contact@bigcorp.com"));

        assertEquals(RoutingResult.Action.CREATE_TICKET, result.action());
        assertEquals(7L, result.customerId());
    }

    // ── Multiple sender domains → same customer ───────────────────────────────

    @Test
    void route_multipleDomainRules_sameCustomer_bothRoute() {
        Long akbankId = 42L;
        CustomerEmailRoutingRule rule1 = rule(akbankId, SenderMatchType.DOMAIN, "akbank.com", 10);
        CustomerEmailRoutingRule rule2 = rule(akbankId, SenderMatchType.DOMAIN, "info-akbank.com.tr", 10);
        when(routingRuleRepository.findAll()).thenReturn(List.of(rule1, rule2));

        RoutingResult r1 = routingService.route(event("ops@akbank.com"));
        RoutingResult r2 = routingService.route(event("noreply@info-akbank.com.tr"));

        assertEquals(RoutingResult.Action.CREATE_TICKET, r1.action());
        assertEquals(akbankId, r1.customerId());
        assertEquals(RoutingResult.Action.CREATE_TICKET, r2.action());
        assertEquals(akbankId, r2.customerId());
    }

    // ── Exact beats domain (specificity) ─────────────────────────────────────

    @Test
    void route_exactEmailRule_winsOverDomainRule() {
        // Two customers: exact rule for ops@bigcorp.com → customer 5
        // Domain rule for bigcorp.com → customer 7
        CustomerEmailRoutingRule exactRule = rule(5L, SenderMatchType.EXACT_EMAIL, "ops@bigcorp.com", 100);
        CustomerEmailRoutingRule domainRule = rule(7L, SenderMatchType.DOMAIN, "bigcorp.com", 10);
        when(routingRuleRepository.findAll()).thenReturn(List.of(domainRule, exactRule));

        RoutingResult result = routingService.route(event("ops@bigcorp.com"));

        // Exact-email rules are evaluated before domain rules regardless of priority order
        assertEquals(RoutingResult.Action.CREATE_TICKET, result.action());
        assertEquals(5L, result.customerId());
    }

    // ── No match → unknown sender policy ─────────────────────────────────────

    @Test
    void route_quarantines_whenNoMatchAndManualReviewPolicy() {
        CustomerEmailSettings settings = new CustomerEmailSettings();
        settings.setUnknownSenderPolicy(UnknownSenderPolicy.MANUAL_REVIEW);
        settings.setIsActive(true);
        when(settingsRepository.findAll()).thenReturn(List.of(settings));

        RoutingResult result = routingService.route(event("stranger@unknown.com"));

        assertEquals(RoutingResult.Action.QUARANTINE, result.action());
    }

    @Test
    void route_ignores_whenNoMatchAndIgnorePolicy() {
        CustomerEmailSettings settings = new CustomerEmailSettings();
        settings.setUnknownSenderPolicy(UnknownSenderPolicy.IGNORE);
        settings.setIsActive(true);
        when(settingsRepository.findAll()).thenReturn(List.of(settings));

        RoutingResult result = routingService.route(event("stranger@unknown.com"));

        assertEquals(RoutingResult.Action.IGNORE, result.action());
    }

    @Test
    void route_quarantines_byDefault_whenNoSettingsConfigured() {
        RoutingResult result = routingService.route(event("stranger@unknown.com"));

        assertEquals(RoutingResult.Action.QUARANTINE, result.action());
        assertNull(result.customerId());
    }

    // ── Display-name in From address is stripped ──────────────────────────────

    @Test
    void route_normalizesDisplayNameInFrom() {
        CustomerEmailRoutingRule rule = rule(3L, SenderMatchType.EXACT_EMAIL, "alice@acme.com", 10);
        when(routingRuleRepository.findAll()).thenReturn(List.of(rule));

        // From header with display name
        RoutingResult result = routingService.route(event("Alice Smith <alice@acme.com>"));

        assertEquals(RoutingResult.Action.CREATE_TICKET, result.action());
        assertEquals(3L, result.customerId());
    }

    // ── Contact is NOT required ───────────────────────────────────────────────

    @Test
    void route_doesNotRequireContactForResolution() {
        // Domain rule should work with no contacts table involved
        CustomerEmailRoutingRule rule = rule(9L, SenderMatchType.DOMAIN, "newcorp.com", 10);
        when(routingRuleRepository.findAll()).thenReturn(List.of(rule));

        RoutingResult result = routingService.route(event("unknown.sender@newcorp.com"));

        assertEquals(RoutingResult.Action.CREATE_TICKET, result.action());
        assertEquals(9L, result.customerId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EmailIngressEvent event(String from) {
        EmailIngressEvent e = new EmailIngressEvent();
        e.setMessageId("<test@example.com>");
        e.setRawFrom(from);
        e.setReceivedAt(Instant.now());
        return e;
    }

    private EmailIngressEvent eventWithInReplyTo(String from, String inReplyTo) {
        EmailIngressEvent e = event(from);
        e.setInReplyTo(inReplyTo);
        return e;
    }

    private CustomerEmailRoutingRule rule(Long customerId, SenderMatchType type, String value, int priority) {
        CustomerEmailRoutingRule rule = new CustomerEmailRoutingRule();
        rule.setCustomerId(customerId);
        rule.setSenderMatchType(type);
        rule.setMatchValue(value);
        rule.setPriority(priority);
        rule.setIsActive(true);
        return rule;
    }

    // ── Subdomain matching ────────────────────────────────────────────────────

    @Test
    void route_matchesSubdomain_whenAllowSubdomainsTrue() {
        CustomerEmailRoutingRule rule = rule(5L, SenderMatchType.DOMAIN, "bigcorp.com", 10);
        rule.setAllowSubdomains(true);
        when(routingRuleRepository.findAll()).thenReturn(List.of(rule));

        RoutingResult result = routingService.route(event("user@mail.bigcorp.com"));

        assertEquals(RoutingResult.Action.CREATE_TICKET, result.action());
        assertEquals(5L, result.customerId());
    }

    @Test
    void route_doesNotMatchSubdomain_whenAllowSubdomainsFalse() {
        CustomerEmailRoutingRule rule = rule(5L, SenderMatchType.DOMAIN, "bigcorp.com", 10);
        rule.setAllowSubdomains(false);
        when(routingRuleRepository.findAll()).thenReturn(List.of(rule));

        RoutingResult result = routingService.route(event("user@mail.bigcorp.com"));

        // Should fall through to unknown sender
        assertEquals(RoutingResult.Action.QUARANTINE, result.action());
    }

    @Test
    void route_exactDomainStillMatchesWhenAllowSubdomainsTrue() {
        CustomerEmailRoutingRule rule = rule(5L, SenderMatchType.DOMAIN, "bigcorp.com", 10);
        rule.setAllowSubdomains(true);
        when(routingRuleRepository.findAll()).thenReturn(List.of(rule));

        RoutingResult result = routingService.route(event("user@bigcorp.com"));

        assertEquals(RoutingResult.Action.CREATE_TICKET, result.action());
        assertEquals(5L, result.customerId());
    }

    // ── Ambiguous domain rule detection ───────────────────────────────────────

    @Test
    void route_quarantines_whenAmbiguousEqualPriorityDomainRules() {
        // Two domain rules at same priority, same domain length — ambiguous
        CustomerEmailRoutingRule rule1 = rule(5L, SenderMatchType.DOMAIN, "acme.com", 10);
        CustomerEmailRoutingRule rule2 = rule(7L, SenderMatchType.DOMAIN, "acme.com", 10);
        when(routingRuleRepository.findAll()).thenReturn(List.of(rule1, rule2));

        RoutingResult result = routingService.route(event("user@acme.com"));

        assertEquals(RoutingResult.Action.QUARANTINE, result.action());
        assertNull(result.customerId());
    }

    @Test
    void route_longerDomainWinsOverShorterDomain_samePriority() {
        // More specific rule (longer domain) should win
        CustomerEmailRoutingRule shortRule  = rule(5L, SenderMatchType.DOMAIN, "corp.com", 10);
        CustomerEmailRoutingRule longerRule = rule(7L, SenderMatchType.DOMAIN, "mail.corp.com", 10);
        longerRule.setAllowSubdomains(false);
        shortRule.setAllowSubdomains(true); // allows sub.corp.com
        when(routingRuleRepository.findAll()).thenReturn(List.of(shortRule, longerRule));

        RoutingResult result = routingService.route(event("user@mail.corp.com"));

        assertEquals(RoutingResult.Action.CREATE_TICKET, result.action());
        assertEquals(7L, result.customerId()); // more specific rule wins
    }

    // ── Mailbox-level unknown sender policy ───────────────────────────────────

    @Test
    void route_usesMailboxPolicy_beforeGlobalPolicy() {
        // Mailbox says IGNORE, global says MANUAL_REVIEW — global should never be consulted
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setUnknownSenderPolicy(UnknownSenderPolicy.IGNORE);
        when(mailboxRepository.findById(42L)).thenReturn(Optional.of(mailbox));

        EmailIngressEvent event = event("stranger@unknown.com");
        event.setMailboxId(42L);

        RoutingResult result = routingService.route(event);

        assertEquals(RoutingResult.Action.IGNORE, result.action());
    }

    @Test
    void route_fallsBackToGlobalPolicy_whenMailboxHasNoPolicy() {
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setUnknownSenderPolicy(null); // not set
        when(mailboxRepository.findById(42L)).thenReturn(Optional.of(mailbox));

        CustomerEmailSettings globalSettings = new CustomerEmailSettings();
        globalSettings.setUnknownSenderPolicy(UnknownSenderPolicy.IGNORE);
        globalSettings.setIsActive(true);
        when(settingsRepository.findAll()).thenReturn(List.of(globalSettings));

        EmailIngressEvent event = event("stranger@unknown.com");
        event.setMailboxId(42L);

        RoutingResult result = routingService.route(event);

        assertEquals(RoutingResult.Action.IGNORE, result.action());
    }

    // ── routeHeaders() — header-only precheck path ────────────────────────────

    @Test
    void routeHeaders_linksToTicket_whenThreadResolvesViaInReplyTo() {
        when(threadingService.resolveTicketId(any(), any())).thenReturn(Optional.of(55L));

        RoutingResult result = routingService.routeHeaders(
                "sender@example.com", "<parent@example.com>", List.of(), null);

        assertEquals(RoutingResult.Action.LINK_TO_TICKET, result.action());
        assertEquals(55L, result.ticketId());
    }

    @Test
    void routeHeaders_createsTicket_forExactEmailRule() {
        CustomerEmailRoutingRule rule = rule(3L, SenderMatchType.EXACT_EMAIL, "ops@bigcorp.com", 10);
        when(routingRuleRepository.findAll()).thenReturn(List.of(rule));

        RoutingResult result = routingService.routeHeaders(
                "ops@bigcorp.com", null, List.of(), null);

        assertEquals(RoutingResult.Action.CREATE_TICKET, result.action());
        assertEquals(3L, result.customerId());
    }

    @Test
    void routeHeaders_createsTicket_forDomainRule() {
        CustomerEmailRoutingRule rule = rule(7L, SenderMatchType.DOMAIN, "bigcorp.com", 10);
        when(routingRuleRepository.findAll()).thenReturn(List.of(rule));

        RoutingResult result = routingService.routeHeaders(
                "any@bigcorp.com", null, List.of(), null);

        assertEquals(RoutingResult.Action.CREATE_TICKET, result.action());
        assertEquals(7L, result.customerId());
    }

    @Test
    void routeHeaders_quarantines_byDefault_whenNoMatch() {
        RoutingResult result = routingService.routeHeaders(
                "stranger@unknown.com", null, List.of(), null);

        assertEquals(RoutingResult.Action.QUARANTINE, result.action());
    }

    @Test
    void routeHeaders_appliesMailboxPolicy_whenNoRuleMatches() {
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setUnknownSenderPolicy(UnknownSenderPolicy.IGNORE);
        when(mailboxRepository.findById(99L)).thenReturn(Optional.of(mailbox));

        RoutingResult result = routingService.routeHeaders(
                "stranger@unknown.com", null, List.of(), 99L);

        assertEquals(RoutingResult.Action.IGNORE, result.action());
    }

    @Test
    void routeHeaders_stripsDisplayName_beforeMatching() {
        CustomerEmailRoutingRule rule = rule(8L, SenderMatchType.EXACT_EMAIL, "alice@acme.com", 10);
        when(routingRuleRepository.findAll()).thenReturn(List.of(rule));

        RoutingResult result = routingService.routeHeaders(
                "Alice Acme <alice@acme.com>", null, List.of(), null);

        assertEquals(RoutingResult.Action.CREATE_TICKET, result.action());
        assertEquals(8L, result.customerId());
    }

    // ── Exact-email ambiguity → QUARANTINE ───────────────────────────────────

    @Test
    void route_quarantines_whenTwoExactRulesForDifferentCustomersMatchSameSender() {
        // Two exact rules for the same address but different customers → operator conflict
        CustomerEmailRoutingRule rule1 = rule(10L, SenderMatchType.EXACT_EMAIL, "shared@partner.com", 10);
        CustomerEmailRoutingRule rule2 = rule(20L, SenderMatchType.EXACT_EMAIL, "shared@partner.com", 5);
        when(routingRuleRepository.findAll()).thenReturn(List.of(rule1, rule2));

        RoutingResult result = routingService.route(event("shared@partner.com"));

        assertEquals(RoutingResult.Action.QUARANTINE, result.action());
        assertNull(result.customerId());
    }

    @Test
    void routeHeaders_quarantines_whenTwoExactRulesForDifferentCustomersMatchSameSender() {
        CustomerEmailRoutingRule rule1 = rule(10L, SenderMatchType.EXACT_EMAIL, "shared@partner.com", 10);
        CustomerEmailRoutingRule rule2 = rule(20L, SenderMatchType.EXACT_EMAIL, "shared@partner.com", 5);
        when(routingRuleRepository.findAll()).thenReturn(List.of(rule1, rule2));

        RoutingResult result = routingService.routeHeaders(
                "shared@partner.com", null, List.of(), null);

        assertEquals(RoutingResult.Action.QUARANTINE, result.action());
    }

    @Test
    void route_doesNotQuarantine_whenTwoExactRulesForSameCustomerMatchSameSender() {
        // Same customer — not ambiguous
        CustomerEmailRoutingRule rule1 = rule(10L, SenderMatchType.EXACT_EMAIL, "ops@partner.com", 10);
        CustomerEmailRoutingRule rule2 = rule(10L, SenderMatchType.EXACT_EMAIL, "ops@partner.com", 5);
        when(routingRuleRepository.findAll()).thenReturn(List.of(rule1, rule2));

        RoutingResult result = routingService.route(event("ops@partner.com"));

        assertEquals(RoutingResult.Action.CREATE_TICKET, result.action());
        assertEquals(10L, result.customerId());
    }
}
