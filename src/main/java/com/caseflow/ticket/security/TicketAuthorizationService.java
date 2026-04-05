package com.caseflow.ticket.security;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.identity.domain.Permission;
import com.caseflow.identity.domain.TicketScope;
import com.caseflow.note.service.NoteService;
import com.caseflow.storage.service.AttachmentService;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.service.TicketQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Central authorization service for ticket-related access decisions.
 *
 * Referenced from @PreAuthorize SpEL as "@ticketAuth":
 *   @PreAuthorize("@ticketAuth.canReadTicket(authentication, #id)")
 *
 * All decisions are based on:
 *   1. Permission codes from the user's role (never role names)
 *   2. TicketScope from the user's role
 *   3. User–ticket relationship (assignment, group membership)
 *
 * If a ticket does not exist, the underlying TicketQueryService throws
 * TicketNotFoundException which propagates through the SpEL evaluation
 * and is handled by GlobalExceptionHandler → 404.
 */
@Service("ticketAuth")
public class TicketAuthorizationService {

    private final TicketQueryService ticketQueryService;
    private final NoteService noteService;
    private final AttachmentService attachmentService;

    public TicketAuthorizationService(TicketQueryService ticketQueryService,
                                      NoteService noteService,
                                      AttachmentService attachmentService) {
        this.ticketQueryService = ticketQueryService;
        this.noteService = noteService;
        this.attachmentService = attachmentService;
    }

    // ── Direct ticket checks ──────────────────────────────────────────────────

    public boolean canReadTicket(Authentication auth, Long ticketId) {
        if (!hasPermission(auth, Permission.TICKET_READ)) return false;
        return inScope(auth, ticketQueryService.getById(ticketId));
    }

    public boolean canReadTicketByPublicId(Authentication auth, UUID ticketPublicId) {
        if (!hasPermission(auth, Permission.TICKET_READ)) return false;
        return inScope(auth, ticketQueryService.getByPublicId(ticketPublicId));
    }

    public boolean canViewTicketEmailByPublicId(Authentication auth, UUID ticketPublicId) {
        if (!hasPermission(auth, Permission.TICKET_EMAIL_VIEW)) return false;
        return inScope(auth, ticketQueryService.getByPublicId(ticketPublicId));
    }

    public boolean canReadTicketByNo(Authentication auth, String ticketNo) {
        if (!hasPermission(auth, Permission.TICKET_READ)) return false;
        return inScope(auth, ticketQueryService.getByTicketNo(ticketNo));
    }

    public boolean canViewAdminPool(Authentication auth) {
        return hasPermission(auth, Permission.ADMIN_POOL_VIEW);
    }

    public boolean canAssignTicket(Authentication auth, Long ticketId, Long targetUserId, Long targetGroupId) {
        if (!hasPermission(auth, Permission.TICKET_ASSIGN)) return false;
        return inScope(auth, ticketQueryService.getById(ticketId));
    }

    public boolean canTransferTicket(Authentication auth, Long ticketId, Long toGroupId) {
        if (!hasPermission(auth, Permission.TICKET_TRANSFER)) return false;
        return inScope(auth, ticketQueryService.getById(ticketId));
    }

    public boolean canChangeTicketStatus(Authentication auth, Long ticketId) {
        if (!hasPermission(auth, Permission.TICKET_STATUS_CHANGE)) return false;
        return inScope(auth, ticketQueryService.getById(ticketId));
    }

    public boolean canCloseTicket(Authentication auth, Long ticketId) {
        if (!hasPermission(auth, Permission.TICKET_CLOSE)) return false;
        return inScope(auth, ticketQueryService.getById(ticketId));
    }

    public boolean canChangePriority(Authentication auth, Long ticketId) {
        if (!hasPermission(auth, Permission.TICKET_PRIORITY_CHANGE)) return false;
        return inScope(auth, ticketQueryService.getById(ticketId));
    }

    public boolean canAddInternalNote(Authentication auth, Long ticketId) {
        if (!hasPermission(auth, Permission.INTERNAL_NOTE_ADD)) return false;
        return inScope(auth, ticketQueryService.getById(ticketId));
    }

    public boolean canSendCustomerReply(Authentication auth, Long ticketId) {
        if (!hasPermission(auth, Permission.CUSTOMER_REPLY_SEND)) return false;
        return inScope(auth, ticketQueryService.getById(ticketId));
    }

    public boolean canViewTicketEmail(Authentication auth, Long ticketId) {
        if (!hasPermission(auth, Permission.TICKET_EMAIL_VIEW)) return false;
        return inScope(auth, ticketQueryService.getById(ticketId));
    }

    public boolean canSendTicketEmailReply(Authentication auth, Long ticketId) {
        if (!hasPermission(auth, Permission.TICKET_EMAIL_REPLY_SEND)) return false;
        return inScope(auth, ticketQueryService.getById(ticketId));
    }

    // ── Indirect checks (via note or attachment → parent ticket) ─────────────

    public boolean canReadNoteById(Authentication auth, Long noteId) {
        if (!hasPermission(auth, Permission.TICKET_READ)) return false;
        Long ticketId = noteService.getById(noteId).getTicketId();
        return inScope(auth, ticketQueryService.getById(ticketId));
    }

    public boolean canReadAttachmentById(Authentication auth, Long attachmentId) {
        if (!hasPermission(auth, Permission.TICKET_READ)) return false;
        Long ticketId = attachmentService.getById(attachmentId).getTicketId();
        if (ticketId == null) return false;
        return inScope(auth, ticketQueryService.getById(ticketId));
    }

    public boolean canDeleteAttachmentById(Authentication auth, Long attachmentId) {
        if (!hasPermission(auth, Permission.TICKET_PRIORITY_CHANGE)) return false;
        Long ticketId = attachmentService.getById(attachmentId).getTicketId();
        if (ticketId == null) return false;
        return inScope(auth, ticketQueryService.getById(ticketId));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private boolean hasPermission(Authentication auth, Permission required) {
        CaseFlowUserDetails details = extractDetails(auth);
        return details != null && details.getPermissions().contains(required);
    }

    /**
     * Returns true if the caller's scope grants access to the given ticket.
     * Scope rules:
     *   ALL               — always true
     *   OWN_GROUPS        — ticket.assignedGroupId must be in the user's groups
     *   OWN_AND_OWN_GROUPS — ticket assigned to user directly, OR in one of their groups
     *   ASSIGNED_ONLY     — ticket.assignedUserId must equal the caller's user ID
     */
    private boolean inScope(Authentication auth, Ticket ticket) {
        CaseFlowUserDetails details = extractDetails(auth);
        if (details == null) return false;

        String scopeName = details.getTicketScope();
        if (scopeName == null) return false;

        TicketScope scope = TicketScope.valueOf(scopeName);
        Long userId = details.getUserId();
        List<Long> groupIds = details.getGroupIds();

        return switch (scope) {
            case ALL -> true;
            case OWN_GROUPS ->
                    ticket.getAssignedGroupId() != null
                    && groupIds.contains(ticket.getAssignedGroupId());
            case OWN_AND_OWN_GROUPS ->
                    userId.equals(ticket.getAssignedUserId())
                    || (ticket.getAssignedGroupId() != null && groupIds.contains(ticket.getAssignedGroupId()));
            case ASSIGNED_ONLY ->
                    userId.equals(ticket.getAssignedUserId());
        };
    }

    private CaseFlowUserDetails extractDetails(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return null;
        return auth.getPrincipal() instanceof CaseFlowUserDetails d ? d : null;
    }
}
