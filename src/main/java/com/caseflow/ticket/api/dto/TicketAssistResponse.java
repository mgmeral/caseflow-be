package com.caseflow.ticket.api.dto;

import com.caseflow.email.api.dto.MailTemplateResponse;
import com.caseflow.ticket.domain.TicketStatus;

import java.util.List;
import java.util.Set;

/**
 * Rule-based assist hints for an agent working a ticket.
 *
 * <h2>recommendedTemplates</h2>
 * Up to 3 mail templates ranked by relevance to the current ticket state.
 * Ranked by: (1) templates whose {@code defaultStatusAfterSend} matches a natural
 * next status for the ticket, (2) templates matching the ticket priority context,
 * (3) recently active templates by alphabetical tiebreak.
 *
 * <h2>suggestedNextStatuses</h2>
 * Statuses that represent the most natural progression from the current status.
 * A subset of {@code allowedTransitions}.
 *
 * <h2>allowedTransitions</h2>
 * Full set of valid transitions from the current status per the state machine.
 */
public record TicketAssistResponse(
        List<MailTemplateResponse> recommendedTemplates,
        List<TicketStatus> suggestedNextStatuses,
        Set<TicketStatus> allowedTransitions
) {}
