package com.caseflow.email.service;

import com.caseflow.common.domain.UnknownSenderPolicy;
import com.caseflow.customer.domain.Contact;
import com.caseflow.customer.domain.Customer;
import com.caseflow.customer.domain.CustomerEmailRoutingRule;
import com.caseflow.customer.domain.CustomerEmailSettings;
import com.caseflow.customer.domain.MatchingStrategy;
import com.caseflow.customer.domain.SenderMatchType;
import com.caseflow.customer.repository.ContactRepository;
import com.caseflow.customer.repository.CustomerEmailRoutingRuleRepository;
import com.caseflow.customer.repository.CustomerEmailSettingsRepository;
import com.caseflow.email.domain.EmailIngressEvent;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailRoutingServiceTest {

    @Mock private ContactRepository contactRepository;
    @Mock private CustomerEmailSettingsRepository settingsRepository;
    @Mock private CustomerEmailRoutingRuleRepository routingRuleRepository;
    @Mock private EmailThreadingService threadingService;

    @InjectMocks
    private EmailRoutingService routingService;

    @BeforeEach
    void defaultStubs() {
        // No rules, no contacts, no settings by default
        lenient().when(routingRuleRepository.findAll()).thenReturn(List.of());
        lenient().when(contactRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        lenient().when(settingsRepository.findAll()).thenReturn(List.of());
        lenient().when(threadingService.resolveTicketId(anyString(), any())).thenReturn(Optional.empty());
    }

    // ── Contact email match → CREATE_TICKET ───────────────────────────────────

    @Test
    void route_createsTicket_forKnownContactEmail() {
        Contact contact = contactWithCustomer("john@acme.com", 10L);
        when(contactRepository.findByEmail("john@acme.com")).thenReturn(Optional.of(contact));

        RoutingResult result = routingService.route(event("john@acme.com"));

        assertEquals(RoutingResult.Action.CREATE_TICKET, result.action());
        assertEquals(10L, result.customerId());
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

    // ── Domain routing rule → CREATE_TICKET ───────────────────────────────────

    @Test
    void route_createsTicket_forDomainRule() {
        CustomerEmailRoutingRule rule = rule(7L, SenderMatchType.DOMAIN, "bigcorp.com", 20);
        when(routingRuleRepository.findAll()).thenReturn(List.of(rule));

        RoutingResult result = routingService.route(event("any.user@bigcorp.com"));

        assertEquals(RoutingResult.Action.CREATE_TICKET, result.action());
        assertEquals(7L, result.customerId());
    }

    // ── Unknown sender + MANUAL_REVIEW → QUARANTINE ───────────────────────────

    @Test
    void route_quarantines_whenNoMatchAndManualReviewPolicy() {
        CustomerEmailSettings settings = new CustomerEmailSettings();
        settings.setUnknownSenderPolicy(UnknownSenderPolicy.MANUAL_REVIEW);
        settings.setMatchingStrategy(MatchingStrategy.CONTACT_FIRST);
        settings.setIsActive(true);
        when(settingsRepository.findAll()).thenReturn(List.of(settings));

        RoutingResult result = routingService.route(event("stranger@unknown.com"));

        assertEquals(RoutingResult.Action.QUARANTINE, result.action());
    }

    // ── Unknown sender + IGNORE → IGNORE ─────────────────────────────────────

    @Test
    void route_ignores_whenNoMatchAndIgnorePolicy() {
        CustomerEmailSettings settings = new CustomerEmailSettings();
        settings.setUnknownSenderPolicy(UnknownSenderPolicy.IGNORE);
        settings.setMatchingStrategy(MatchingStrategy.CONTACT_FIRST);
        settings.setIsActive(true);
        when(settingsRepository.findAll()).thenReturn(List.of(settings));

        RoutingResult result = routingService.route(event("stranger@unknown.com"));

        assertEquals(RoutingResult.Action.IGNORE, result.action());
    }

    // ── Unknown sender with no settings → QUARANTINE (default) ───────────────

    @Test
    void route_quarantines_byDefault_whenNoSettingsConfigured() {
        RoutingResult result = routingService.route(event("stranger@unknown.com"));

        assertEquals(RoutingResult.Action.QUARANTINE, result.action());
        assertNull(result.customerId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EmailIngressEvent event(String from) {
        EmailIngressEvent e = new EmailIngressEvent();
        e.setMessageId("<test@example.com>");
        e.setRawFrom(from);
        e.setReceivedAt(Instant.now());
        return e;
    }

    private Contact contactWithCustomer(String email, Long customerId) {
        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn(customerId);
        Contact contact = mock(Contact.class);
        when(contact.getCustomer()).thenReturn(customer);
        return contact;
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

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
