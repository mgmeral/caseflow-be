package com.caseflow.notification.service;

import com.caseflow.identity.repository.UserRepository;
import com.caseflow.notification.domain.NotificationType;
import com.caseflow.notification.domain.UserNotification;
import com.caseflow.notification.repository.UserNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private UserNotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private NotificationService sut;

    private final Long ticketId = 10L;
    private final UUID ticketPublicId = UUID.randomUUID();
    private final String ticketNo = "TKT-0000001";
    private final Long groupId = 5L;
    private final Long actorUserId = 99L;

    // ── notifyGroupTicketCreated ──────────────────────────────────────────────

    @Test
    void notifyGroupTicketCreated_createsOneNotificationPerActiveGroupMember() {
        when(userRepository.findActiveUserIdsByGroupId(groupId)).thenReturn(List.of(1L, 2L, 3L));
        when(notificationRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        sut.notifyGroupTicketCreated(ticketId, ticketPublicId, ticketNo, groupId, actorUserId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserNotification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());

        List<UserNotification> saved = captor.getValue();
        assertThat(saved).hasSize(3);
        assertThat(saved).allMatch(n -> n.getType() == NotificationType.TICKET_CREATED_FOR_GROUP);
        assertThat(saved).allMatch(n -> ticketId.equals(n.getTicketId()));
        assertThat(saved).allMatch(n -> ticketNo.equals(n.getTicketNo()));
        assertThat(saved).allMatch(n -> groupId.equals(n.getGroupId()));
        assertThat(saved).extracting(UserNotification::getUserId)
                .containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void notifyGroupTicketCreated_skipsInsert_whenGroupHasNoActiveMembers() {
        when(userRepository.findActiveUserIdsByGroupId(groupId)).thenReturn(List.of());

        sut.notifyGroupTicketCreated(ticketId, ticketPublicId, ticketNo, groupId, actorUserId);

        verify(notificationRepository, never()).saveAll(any());
    }

    // ── notifyUserAssigned ────────────────────────────────────────────────────

    @Test
    void notifyUserAssigned_createsNotification_forAssignedUser() {
        Long userId = 7L;
        when(notificationRepository.existsByUserIdAndTicketIdAndTypeAndIsReadFalse(
                userId, ticketId, NotificationType.TICKET_ASSIGNED_TO_USER)).thenReturn(false);
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sut.notifyUserAssigned(ticketId, ticketPublicId, ticketNo,
                userId, NotificationType.TICKET_ASSIGNED_TO_USER, actorUserId);

        ArgumentCaptor<UserNotification> captor = ArgumentCaptor.forClass(UserNotification.class);
        verify(notificationRepository).save(captor.capture());

        UserNotification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getType()).isEqualTo(NotificationType.TICKET_ASSIGNED_TO_USER);
        assertThat(saved.getTicketId()).isEqualTo(ticketId);
        assertThat(saved.getIsRead()).isFalse();
    }

    @Test
    void notifyUserAssigned_skipsDuplicate_whenUnreadNotificationAlreadyExists() {
        Long userId = 7L;
        when(notificationRepository.existsByUserIdAndTicketIdAndTypeAndIsReadFalse(
                userId, ticketId, NotificationType.TICKET_ASSIGNED_TO_USER)).thenReturn(true);

        sut.notifyUserAssigned(ticketId, ticketPublicId, ticketNo,
                userId, NotificationType.TICKET_ASSIGNED_TO_USER, actorUserId);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void notifyUserAssigned_usesReassignedTitle_forReassignType() {
        Long userId = 8L;
        when(notificationRepository.existsByUserIdAndTicketIdAndTypeAndIsReadFalse(
                userId, ticketId, NotificationType.TICKET_REASSIGNED_TO_USER)).thenReturn(false);
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sut.notifyUserAssigned(ticketId, ticketPublicId, ticketNo,
                userId, NotificationType.TICKET_REASSIGNED_TO_USER, actorUserId);

        ArgumentCaptor<UserNotification> captor = ArgumentCaptor.forClass(UserNotification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).contains("reassigned");
    }

    // ── markReadByTicket ──────────────────────────────────────────────────────

    @Test
    void markReadByTicket_callsRepositoryWithUserAndTicket() {
        Long userId = 3L;
        when(notificationRepository.markReadByUserAndTicket(eq(userId), eq(ticketId), any(Instant.class)))
                .thenReturn(2);

        int count = sut.markReadByTicket(userId, ticketId);

        assertThat(count).isEqualTo(2);
        verify(notificationRepository).markReadByUserAndTicket(eq(userId), eq(ticketId), any(Instant.class));
    }

    @Test
    void markReadByTicket_doesNotClear_otherUsersNotifications() {
        Long userA = 1L;
        Long userB = 2L;
        when(notificationRepository.markReadByUserAndTicket(eq(userA), eq(ticketId), any())).thenReturn(1);

        sut.markReadByTicket(userA, ticketId);

        // Only userA's notifications are cleared — userB's repository method is NEVER called
        verify(notificationRepository, never()).markReadByUserAndTicket(eq(userB), eq(ticketId), any());
    }

    // ── markReadById ──────────────────────────────────────────────────────────

    @Test
    void markReadById_returnsTrue_whenUpdated() {
        when(notificationRepository.markReadByIdAndUser(eq(42L), eq(3L), any())).thenReturn(1);

        boolean result = sut.markReadById(3L, 42L);

        assertThat(result).isTrue();
    }

    @Test
    void markReadById_returnsFalse_whenNotFoundOrAlreadyRead() {
        when(notificationRepository.markReadByIdAndUser(eq(99L), eq(3L), any())).thenReturn(0);

        boolean result = sut.markReadById(3L, 99L);

        assertThat(result).isFalse();
    }

    // ── unreadCount ───────────────────────────────────────────────────────────

    @Test
    void unreadCount_returnsCountFromRepository() {
        when(notificationRepository.countByUserIdAndIsReadFalse(5L)).thenReturn(7L);

        long count = sut.unreadCount(5L);

        assertThat(count).isEqualTo(7L);
    }

    @Test
    void unreadCount_returnsZero_whenNoUnread() {
        when(notificationRepository.countByUserIdAndIsReadFalse(5L)).thenReturn(0L);

        long count = sut.unreadCount(5L);

        assertThat(count).isEqualTo(0L);
    }

    // ── list ─────────────────────────────────────────────────────────────────

    @Test
    void list_returnsPaginatedNotificationsForUser() {
        UserNotification n = new UserNotification();
        n.setUserId(4L);
        n.setType(NotificationType.TICKET_ASSIGNED_TO_USER);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(4L), any()))
                .thenReturn(new PageImpl<>(List.of(n)));

        var page = sut.list(4L, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getUserId()).isEqualTo(4L);
    }

    // ── markAllRead ───────────────────────────────────────────────────────────

    @Test
    void markAllRead_callsRepositoryForUser() {
        when(notificationRepository.markAllReadByUser(eq(3L), any())).thenReturn(5);

        int count = sut.markAllRead(3L);

        assertThat(count).isEqualTo(5);
    }
}
