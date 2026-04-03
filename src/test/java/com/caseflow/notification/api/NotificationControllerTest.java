package com.caseflow.notification.api;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.identity.domain.Role;
import com.caseflow.identity.domain.User;
import com.caseflow.notification.api.dto.NotificationResponse;
import com.caseflow.notification.api.dto.UnreadCountResponse;
import com.caseflow.notification.domain.NotificationType;
import com.caseflow.notification.domain.UserNotification;
import com.caseflow.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock private NotificationService notificationService;

    @InjectMocks
    private NotificationController sut;

    private CaseFlowUserDetails principal;

    @BeforeEach
    void setUp() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 42L);
        user.setUsername("agent");
        user.setEmail("agent@example.com");
        user.setFullName("Agent User");
        user.setIsActive(true);
        Role role = new Role();
        role.setCode("AGENT");
        role.setName("Agent");
        user.setRole(role);
        principal = new CaseFlowUserDetails(user);
    }

    // ── list ─────────────────────────────────────────────────────────────────

    @Test
    void list_returnsPageOfNotifications_forCurrentUser() {
        UserNotification n = new UserNotification();
        n.setUserId(42L);
        n.setType(NotificationType.TICKET_ASSIGNED_TO_USER);
        n.setTitle("Ticket assigned to you");
        n.setTicketId(1L);
        n.setTicketPublicId(UUID.randomUUID());
        n.setTicketNo("TKT-0000001");

        when(notificationService.list(eq(42L), any()))
                .thenReturn(new PageImpl<>(List.of(n)));

        var result = sut.list(principal, 0, 20);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().items()).hasSize(1);
        assertThat(result.getBody().items().get(0).userId()).isEqualTo(42L);
    }

    @Test
    void list_queriesOnlyCurrentUser() {
        when(notificationService.list(eq(42L), any()))
                .thenReturn(new PageImpl<>(List.of()));

        sut.list(principal, 0, 20);

        verify(notificationService).list(eq(42L), any());
    }

    // ── unreadCount ───────────────────────────────────────────────────────────

    @Test
    void unreadCount_returnsCount_forCurrentUser() {
        when(notificationService.unreadCount(42L)).thenReturn(5L);

        ResponseEntity<UnreadCountResponse> result = sut.unreadCount(principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().unreadCount()).isEqualTo(5L);
    }

    @Test
    void unreadCount_returnsZero_whenNoUnread() {
        when(notificationService.unreadCount(42L)).thenReturn(0L);

        ResponseEntity<UnreadCountResponse> result = sut.unreadCount(principal);

        assertThat(result.getBody().unreadCount()).isEqualTo(0L);
    }

    // ── markRead ──────────────────────────────────────────────────────────────

    @Test
    void markRead_returns200_whenUpdated() {
        when(notificationService.markReadById(42L, 99L)).thenReturn(true);

        ResponseEntity<Void> result = sut.markRead(99L, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void markRead_returns404_whenNotFoundOrAlreadyRead() {
        when(notificationService.markReadById(42L, 999L)).thenReturn(false);

        ResponseEntity<Void> result = sut.markRead(999L, principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── markAllRead ───────────────────────────────────────────────────────────

    @Test
    void markAllRead_returns200_andCallsService() {
        when(notificationService.markAllRead(42L)).thenReturn(3);

        ResponseEntity<Void> result = sut.markAllRead(principal);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(notificationService).markAllRead(42L);
    }
}
