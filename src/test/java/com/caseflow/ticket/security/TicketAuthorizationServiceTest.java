package com.caseflow.ticket.security;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.identity.domain.Permission;
import com.caseflow.note.domain.Note;
import com.caseflow.note.service.NoteService;
import com.caseflow.storage.service.AttachmentService;
import com.caseflow.ticket.domain.AttachmentMetadata;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.service.TicketQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TicketAuthorizationService.
 *
 * Key assertions:
 * - Permissions drive access, never role names
 * - Scope rules correctly limit visibility
 * - Missing permission → false regardless of scope
 * - TicketNotFoundException propagates (→ 404, not 403)
 * - Non-CaseFlowUserDetails principal → false
 */
@ExtendWith(MockitoExtension.class)
class TicketAuthorizationServiceTest {

    @Mock private TicketQueryService ticketQueryService;
    @Mock private NoteService noteService;
    @Mock private AttachmentService attachmentService;

    @InjectMocks
    private TicketAuthorizationService service;

    // ── canReadTicket — permission gate ──────────────────────────────────────

    @Test
    void canReadTicket_returnsFalse_whenPermissionAbsent() {
        Authentication auth = authWith(Set.of(), "ALL", 1L, List.of());
        // No getById stub needed — service exits before reaching it when permission is absent
        assertFalse(service.canReadTicket(auth, 1L));
    }

    @Test
    void canReadTicket_returnsTrue_withPermission_andScopeAll() {
        Authentication auth = authWith(Set.of(Permission.TICKET_READ), "ALL", 1L, List.of());
        when(ticketQueryService.getById(1L)).thenReturn(ticket(null, null));
        assertTrue(service.canReadTicket(auth, 1L));
    }

    // ── canReadTicket — scope rules ───────────────────────────────────────────

    @Test
    void canReadTicket_returnsTrue_whenAssignedOnly_userIsAssigned() {
        Authentication auth = authWith(Set.of(Permission.TICKET_READ), "ASSIGNED_ONLY", 1L, List.of());
        when(ticketQueryService.getById(1L)).thenReturn(ticket(1L, null));
        assertTrue(service.canReadTicket(auth, 1L));
    }

    @Test
    void canReadTicket_returnsFalse_whenAssignedOnly_userNotAssigned() {
        Authentication auth = authWith(Set.of(Permission.TICKET_READ), "ASSIGNED_ONLY", 1L, List.of());
        when(ticketQueryService.getById(1L)).thenReturn(ticket(99L, null));
        assertFalse(service.canReadTicket(auth, 1L));
    }

    @Test
    void canReadTicket_returnsTrue_whenOwnGroups_groupMatches() {
        Authentication auth = authWith(Set.of(Permission.TICKET_READ), "OWN_GROUPS", 1L, List.of(5L));
        when(ticketQueryService.getById(1L)).thenReturn(ticket(null, 5L));
        assertTrue(service.canReadTicket(auth, 1L));
    }

    @Test
    void canReadTicket_returnsFalse_whenOwnGroups_groupDoesNotMatch() {
        Authentication auth = authWith(Set.of(Permission.TICKET_READ), "OWN_GROUPS", 1L, List.of(5L));
        when(ticketQueryService.getById(1L)).thenReturn(ticket(null, 99L));
        assertFalse(service.canReadTicket(auth, 1L));
    }

    @Test
    void canReadTicket_returnsTrue_whenOwnAndOwnGroups_userAssigned() {
        Authentication auth = authWith(Set.of(Permission.TICKET_READ), "OWN_AND_OWN_GROUPS", 1L, List.of(5L));
        when(ticketQueryService.getById(1L)).thenReturn(ticket(1L, 99L));
        assertTrue(service.canReadTicket(auth, 1L));
    }

    @Test
    void canReadTicket_returnsTrue_whenOwnAndOwnGroups_groupMatches() {
        Authentication auth = authWith(Set.of(Permission.TICKET_READ), "OWN_AND_OWN_GROUPS", 1L, List.of(5L));
        when(ticketQueryService.getById(1L)).thenReturn(ticket(99L, 5L));
        assertTrue(service.canReadTicket(auth, 1L));
    }

    @Test
    void canReadTicket_returnsFalse_whenOwnAndOwnGroups_neither() {
        Authentication auth = authWith(Set.of(Permission.TICKET_READ), "OWN_AND_OWN_GROUPS", 1L, List.of(5L));
        when(ticketQueryService.getById(1L)).thenReturn(ticket(99L, 99L));
        assertFalse(service.canReadTicket(auth, 1L));
    }

    // ── canViewAdminPool ──────────────────────────────────────────────────────

    @Test
    void canViewAdminPool_returnsTrue_whenPermissionPresent() {
        Authentication auth = authWith(Set.of(Permission.ADMIN_POOL_VIEW), "ALL", 1L, List.of());
        assertTrue(service.canViewAdminPool(auth));
    }

    @Test
    void canViewAdminPool_returnsFalse_whenPermissionAbsent() {
        Authentication auth = authWith(Set.of(Permission.TICKET_READ), "ALL", 1L, List.of());
        assertFalse(service.canViewAdminPool(auth));
    }

    // ── Role names are irrelevant ─────────────────────────────────────────────

    @Test
    void canReadTicket_returnsTrue_forCustomRoleNameWithCorrectPermission() {
        // The role could be called anything — only Permission and TicketScope matter.
        // This auth represents a custom role with TICKET_READ and ALL scope.
        Authentication auth = authWith(Set.of(Permission.TICKET_READ), "ALL", 1L, List.of());
        when(ticketQueryService.getById(1L)).thenReturn(ticket(null, null));
        assertTrue(service.canReadTicket(auth, 1L));
    }

    @Test
    void canReadTicket_returnsFalse_withoutPermission_evenIfScopeIsAll() {
        // ALL scope but no permission → access denied (service exits at permission check)
        Authentication auth = authWith(Set.of(), "ALL", 1L, List.of());
        assertFalse(service.canReadTicket(auth, 1L));
    }

    // ── TicketNotFoundException propagates ────────────────────────────────────

    @Test
    void canReadTicket_propagatesTicketNotFoundException() {
        Authentication auth = authWith(Set.of(Permission.TICKET_READ), "ALL", 1L, List.of());
        when(ticketQueryService.getById(404L)).thenThrow(new TicketNotFoundException(404L));
        assertThrows(TicketNotFoundException.class, () -> service.canReadTicket(auth, 404L));
    }

    // ── Null / invalid auth ───────────────────────────────────────────────────

    @Test
    void canReadTicket_returnsFalse_whenAuthIsNull() {
        assertFalse(service.canReadTicket(null, 1L));
    }

    @Test
    void canReadTicket_returnsFalse_whenPrincipalIsNotCaseFlowUserDetails() {
        Authentication auth = new TestingAuthenticationToken("anonymous", null);
        assertFalse(service.canReadTicket(auth, 1L));
    }

    // ── Indirect checks via note and attachment ───────────────────────────────

    @Test
    void canReadNoteById_returnsTrue_whenPermissionPresentAndScopeAll() {
        Authentication auth = authWith(Set.of(Permission.TICKET_READ), "ALL", 1L, List.of());
        Note note = new Note();
        note.setTicketId(10L);
        when(noteService.getById(5L)).thenReturn(note);
        when(ticketQueryService.getById(10L)).thenReturn(ticket(null, null));
        assertTrue(service.canReadNoteById(auth, 5L));
    }

    @Test
    void canReadAttachmentById_returnsTrue_whenPermissionPresentAndScopeAll() {
        Authentication auth = authWith(Set.of(Permission.TICKET_READ), "ALL", 1L, List.of());
        AttachmentMetadata attachment = new AttachmentMetadata();
        attachment.setTicketId(10L);
        when(attachmentService.getById(7L)).thenReturn(attachment);
        when(ticketQueryService.getById(10L)).thenReturn(ticket(null, null));
        assertTrue(service.canReadAttachmentById(auth, 7L));
    }

    @Test
    void canReadAttachmentById_returnsFalse_whenAttachmentHasNoTicket() {
        Authentication auth = authWith(Set.of(Permission.TICKET_READ), "ALL", 1L, List.of());
        AttachmentMetadata attachment = new AttachmentMetadata();
        // ticketId is null (email-only attachment)
        when(attachmentService.getById(7L)).thenReturn(attachment);
        assertFalse(service.canReadAttachmentById(auth, 7L));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Authentication authWith(Set<Permission> permissions, String scope, Long userId, List<Long> groupIds) {
        CaseFlowUserDetails details = mock(CaseFlowUserDetails.class);
        // lenient: some tests exit early (e.g. permission denied) without calling scope/userId/groupIds
        lenient().when(details.getPermissions()).thenReturn(permissions);
        lenient().when(details.getTicketScope()).thenReturn(scope);
        lenient().when(details.getUserId()).thenReturn(userId);
        lenient().when(details.getGroupIds()).thenReturn(groupIds);
        // Default TestingAuthenticationToken(principal, credentials) is NOT authenticated — must set explicitly
        TestingAuthenticationToken auth = new TestingAuthenticationToken(details, null);
        auth.setAuthenticated(true);
        return auth;
    }

    private Ticket ticket(Long assignedUserId, Long assignedGroupId) {
        Ticket t = new Ticket();
        t.setAssignedUserId(assignedUserId);
        t.setAssignedGroupId(assignedGroupId);
        return t;
    }
}
