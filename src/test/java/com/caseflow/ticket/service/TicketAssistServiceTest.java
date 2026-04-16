package com.caseflow.ticket.service;

import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.email.domain.MailTemplate;
import com.caseflow.email.service.MailTemplateService;
import com.caseflow.ticket.api.dto.TicketAssistResponse;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.state.TicketStateMachineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketAssistServiceTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private MailTemplateService mailTemplateService;
    @Mock private TicketStateMachineService stateMachineService;

    @InjectMocks
    private TicketAssistService sut;

    private Ticket ticket;

    @BeforeEach
    void setUp() {
        ticket = new Ticket();
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticket.setPriority(TicketPriority.MEDIUM);
        setCreatedAt(ticket, Instant.now());
    }

    @Test
    void assist_throwsNotFound_whenTicketDoesNotExist() {
        when(ticketRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.assist(99L))
                .isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void assist_returnsAllowedTransitions_fromStateMachine() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(stateMachineService.allowedTransitions(TicketStatus.IN_PROGRESS))
                .thenReturn(EnumSet.of(TicketStatus.WAITING_CUSTOMER, TicketStatus.RESOLVED, TicketStatus.CLOSED));
        when(mailTemplateService.search(null, true, null)).thenReturn(List.of());

        TicketAssistResponse result = sut.assist(1L);

        assertThat(result.allowedTransitions())
                .contains(TicketStatus.WAITING_CUSTOMER, TicketStatus.RESOLVED);
    }

    @Test
    void assist_suggestsWaitingCustomerAndResolved_forInProgress() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(stateMachineService.allowedTransitions(TicketStatus.IN_PROGRESS))
                .thenReturn(EnumSet.of(TicketStatus.WAITING_CUSTOMER, TicketStatus.RESOLVED,
                        TicketStatus.ASSIGNED, TicketStatus.CLOSED));
        when(mailTemplateService.search(null, true, null)).thenReturn(List.of());

        TicketAssistResponse result = sut.assist(1L);

        assertThat(result.suggestedNextStatuses())
                .containsExactly(TicketStatus.WAITING_CUSTOMER, TicketStatus.RESOLVED);
    }

    @Test
    void assist_recommendsTemplates_rankedByDefaultStatusAfterSend() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(stateMachineService.allowedTransitions(TicketStatus.IN_PROGRESS))
                .thenReturn(EnumSet.of(TicketStatus.WAITING_CUSTOMER, TicketStatus.RESOLVED));

        MailTemplate matchingTemplate = template("FOLLOW_UP", "WAITING_CUSTOMER", true);
        MailTemplate genericTemplate = template("ACK", null, true);
        when(mailTemplateService.search(null, true, null))
                .thenReturn(List.of(matchingTemplate, genericTemplate));

        TicketAssistResponse result = sut.assist(1L);

        // matchingTemplate should be ranked first because defaultStatusAfterSend = WAITING_CUSTOMER
        assertThat(result.recommendedTemplates()).hasSize(2);
        assertThat(result.recommendedTemplates().get(0).code()).isEqualTo("FOLLOW_UP");
    }

    @Test
    void assist_capsRecommendedTemplatesAtThree() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(stateMachineService.allowedTransitions(TicketStatus.IN_PROGRESS))
                .thenReturn(EnumSet.of(TicketStatus.WAITING_CUSTOMER));
        when(mailTemplateService.search(null, true, null))
                .thenReturn(List.of(
                        template("T1", null, true),
                        template("T2", null, true),
                        template("T3", null, true),
                        template("T4", null, true),
                        template("T5", null, true)
                ));

        TicketAssistResponse result = sut.assist(1L);

        assertThat(result.recommendedTemplates()).hasSize(3);
    }

    @Test
    void assist_returnsEmptyTemplates_whenNoneConfigured() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(stateMachineService.allowedTransitions(TicketStatus.IN_PROGRESS))
                .thenReturn(EnumSet.of(TicketStatus.RESOLVED));
        when(mailTemplateService.search(null, true, null)).thenReturn(List.of());

        TicketAssistResponse result = sut.assist(1L);

        assertThat(result.recommendedTemplates()).isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void setCreatedAt(Ticket ticket, Instant value) {
        try {
            var field = Ticket.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(ticket, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private MailTemplate template(String code, String defaultStatusAfterSend, boolean customerVisible) {
        MailTemplate t = new MailTemplate();
        t.setCode(code);
        t.setName(code);
        t.setHtmlTemplate("<p>body</p>");
        t.setPlainTextTemplate("body");
        t.setIsActive(true);
        t.setIsBuiltIn(false);
        t.setDefaultStatusAfterSend(defaultStatusAfterSend);
        t.setCustomerVisible(customerVisible);
        return t;
    }
}
