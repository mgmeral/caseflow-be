package com.caseflow.ticket.service;

import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.email.api.dto.MailTemplateResponse;
import com.caseflow.email.domain.MailTemplate;
import com.caseflow.email.service.MailTemplateService;
import com.caseflow.ticket.api.dto.TicketAssistResponse;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.state.TicketStateMachineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Rule-based assist hints for agents working a ticket.
 *
 * <h2>Template recommendation algorithm</h2>
 * <ol>
 *   <li>Templates whose {@code defaultStatusAfterSend} matches a primary next status
 *       for the current ticket state are ranked first.</li>
 *   <li>Templates that are {@code customerVisible=true} are preferred over internal ones
 *       unless the ticket is in WAITING_CUSTOMER (internal follow-up context).</li>
 *   <li>Remaining active templates are appended alphabetically as generic options.</li>
 *   <li>List is capped at 3 recommendations.</li>
 * </ol>
 *
 * <h2>Suggested next statuses</h2>
 * Derived from the state machine's allowed transitions, filtered to the most contextually
 * appropriate subset (e.g. after sending a reply, WAITING_CUSTOMER is the primary suggestion).
 */
@Service
public class TicketAssistService {

    private static final int MAX_RECOMMENDED_TEMPLATES = 3;

    private final TicketRepository ticketRepository;
    private final MailTemplateService mailTemplateService;
    private final TicketStateMachineService stateMachineService;

    public TicketAssistService(TicketRepository ticketRepository,
                                MailTemplateService mailTemplateService,
                                TicketStateMachineService stateMachineService) {
        this.ticketRepository = ticketRepository;
        this.mailTemplateService = mailTemplateService;
        this.stateMachineService = stateMachineService;
    }

    /**
     * Returns assist hints for the ticket identified by {@code ticketId}.
     *
     * @throws TicketNotFoundException if no ticket exists with the given id
     */
    @Transactional(readOnly = true)
    public TicketAssistResponse assist(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        Set<TicketStatus> allowed = stateMachineService.allowedTransitions(ticket.getStatus());
        List<TicketStatus> suggested = computeSuggestedNextStatuses(ticket.getStatus(), allowed);
        List<MailTemplateResponse> recommended = computeRecommendedTemplates(ticket, suggested);

        return new TicketAssistResponse(recommended, suggested, allowed);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns the primary next statuses for the current state — the most natural
     * 1-2 transitions an agent would take, not the full allowed set.
     */
    private List<TicketStatus> computeSuggestedNextStatuses(TicketStatus current,
                                                              Set<TicketStatus> allowed) {
        return switch (current) {
            case NEW        -> filterAllowed(allowed, TicketStatus.TRIAGED, TicketStatus.ASSIGNED);
            case TRIAGED    -> filterAllowed(allowed, TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS);
            case ASSIGNED   -> filterAllowed(allowed, TicketStatus.IN_PROGRESS, TicketStatus.WAITING_CUSTOMER);
            case IN_PROGRESS -> filterAllowed(allowed, TicketStatus.WAITING_CUSTOMER, TicketStatus.RESOLVED);
            case WAITING_CUSTOMER -> filterAllowed(allowed, TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED);
            case RESOLVED   -> filterAllowed(allowed, TicketStatus.CLOSED);
            case CLOSED     -> filterAllowed(allowed, TicketStatus.REOPENED);
            case REOPENED   -> filterAllowed(allowed, TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED);
        };
    }

    private List<TicketStatus> filterAllowed(Set<TicketStatus> allowed, TicketStatus... candidates) {
        List<TicketStatus> result = new ArrayList<>();
        for (TicketStatus s : candidates) {
            if (allowed.contains(s)) result.add(s);
        }
        return result;
    }

    /**
     * Ranks active mail templates by relevance to the current ticket context.
     */
    private List<MailTemplateResponse> computeRecommendedTemplates(Ticket ticket,
                                                                     List<TicketStatus> suggested) {
        List<MailTemplate> allActive = mailTemplateService.search(null, true, null);

        // Tier 1: templates whose defaultStatusAfterSend aligns with a suggested next status
        List<MailTemplate> tier1 = new ArrayList<>();
        // Tier 2: customer-visible templates (or internal-only when waiting on customer)
        List<MailTemplate> tier2 = new ArrayList<>();
        // Tier 3: everything else
        List<MailTemplate> tier3 = new ArrayList<>();

        boolean preferInternal = ticket.getStatus() == TicketStatus.WAITING_CUSTOMER;

        for (MailTemplate t : allActive) {
            if (matchesSuggestedTransition(t, suggested)) {
                tier1.add(t);
            } else if (preferInternal ? isInternal(t) : isCustomerVisible(t)) {
                tier2.add(t);
            } else {
                tier3.add(t);
            }
        }

        List<MailTemplate> ranked = new ArrayList<>();
        ranked.addAll(tier1);
        ranked.addAll(tier2);
        ranked.addAll(tier3);

        return ranked.stream()
                .limit(MAX_RECOMMENDED_TEMPLATES)
                .map(MailTemplateResponse::from)
                .toList();
    }

    private boolean matchesSuggestedTransition(MailTemplate t, List<TicketStatus> suggested) {
        if (t.getDefaultStatusAfterSend() == null) return false;
        try {
            TicketStatus afterSend = TicketStatus.valueOf(t.getDefaultStatusAfterSend());
            return suggested.contains(afterSend);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isCustomerVisible(MailTemplate t) {
        return t.getCustomerVisible() == null || Boolean.TRUE.equals(t.getCustomerVisible());
    }

    private boolean isInternal(MailTemplate t) {
        return Boolean.FALSE.equals(t.getCustomerVisible());
    }
}
