package com.caseflow.notification.service;

import com.caseflow.identity.repository.UserRepository;
import com.caseflow.notification.domain.NotificationType;
import com.caseflow.notification.domain.UserNotification;
import com.caseflow.notification.repository.UserNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Manages user-specific notification records for ticket lifecycle events.
 *
 * <h2>Business rules</h2>
 * <ul>
 *   <li>When a ticket is created for a group, each active group member gets an unread notification.</li>
 *   <li>When a ticket is assigned or reassigned to a user, that user gets an unread notification.</li>
 *   <li>No duplicate notifications are created for no-op reassignments (user already has unread
 *       notification of the same type for the same ticket).</li>
 *   <li>Read state is per-user — marking read for user A does not affect user B.</li>
 * </ul>
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final UserNotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(UserNotificationRepository notificationRepository,
                               UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates unread notifications for all active members of the given group when a ticket is
     * created and associated with that group.
     *
     * @param ticketId       internal ticket id
     * @param ticketPublicId stable public UUID (for safe storage paths and external refs)
     * @param ticketNo       human-readable ticket reference
     * @param groupId        the group the ticket was created for
     * @param actorUserId    user who created the ticket (null for system/email ingress)
     */
    @Transactional
    public void notifyGroupTicketCreated(Long ticketId, UUID ticketPublicId, String ticketNo,
                                         Long groupId, Long actorUserId) {
        List<Long> activeUserIds = userRepository.findActiveUserIdsByGroupId(groupId);
        if (activeUserIds.isEmpty()) {
            log.debug("NOTIFICATION_CREATED skipped — no active members in groupId: {}", groupId);
            return;
        }

        List<UserNotification> notifications = activeUserIds.stream()
                .map(userId -> build(userId, NotificationType.TICKET_CREATED_FOR_GROUP,
                        "New ticket in your group",
                        "Ticket " + ticketNo + " was created in your group",
                        ticketId, ticketPublicId, ticketNo, groupId, actorUserId))
                .toList();

        notificationRepository.saveAll(notifications);
        log.info("NOTIFICATION_CREATED type: {}, ticketId: {}, groupId: {}, recipientCount: {}",
                NotificationType.TICKET_CREATED_FOR_GROUP, ticketId, groupId, notifications.size());
    }

    /**
     * Creates an unread notification for a specific user when a ticket is assigned or reassigned
     * to them. Skips if the user already has an unread notification of the same type for the same
     * ticket (no-op reassignment guard).
     *
     * @param ticketId       internal ticket id
     * @param ticketPublicId stable public UUID
     * @param ticketNo       human-readable ticket reference
     * @param targetUserId   the user receiving the notification
     * @param type           {@link NotificationType#TICKET_ASSIGNED_TO_USER} or
     *                       {@link NotificationType#TICKET_REASSIGNED_TO_USER}
     * @param actorUserId    user who performed the assignment (null for system)
     */
    @Transactional
    public void notifyUserAssigned(Long ticketId, UUID ticketPublicId, String ticketNo,
                                   Long targetUserId, NotificationType type, Long actorUserId) {
        if (notificationRepository.existsByUserIdAndTicketIdAndTypeAndIsReadFalse(
                targetUserId, ticketId, type)) {
            log.debug("NOTIFICATION_CREATED skipped (duplicate unread) — userId: {}, ticketId: {}, type: {}",
                    targetUserId, ticketId, type);
            return;
        }

        String title = (type == NotificationType.TICKET_REASSIGNED_TO_USER)
                ? "Ticket reassigned to you"
                : "Ticket assigned to you";
        String message = "Ticket " + ticketNo + " was assigned to you";

        UserNotification notification = build(targetUserId, type, title, message,
                ticketId, ticketPublicId, ticketNo, null, actorUserId);
        notificationRepository.save(notification);
        log.info("NOTIFICATION_CREATED type: {}, ticketId: {}, userId: {}", type, ticketId, targetUserId);
    }

    /**
     * Marks all unread notifications for the given user+ticket as read.
     * Called when a user opens the ticket detail view.
     *
     * @return number of records updated
     */
    @Transactional
    public int markReadByTicket(Long userId, Long ticketId) {
        int count = notificationRepository.markReadByUserAndTicket(userId, ticketId, Instant.now());
        if (count > 0) {
            log.info("NOTIFICATION_MARK_READ userId: {}, ticketId: {}, count: {}", userId, ticketId, count);
        }
        return count;
    }

    /**
     * Marks a single notification as read, verifying ownership.
     *
     * @return true if the notification was found and updated
     */
    @Transactional
    public boolean markReadById(Long userId, Long notificationId) {
        int count = notificationRepository.markReadByIdAndUser(notificationId, userId, Instant.now());
        if (count > 0) {
            log.info("NOTIFICATION_MARK_READ userId: {}, notificationId: {}", userId, notificationId);
        }
        return count > 0;
    }

    /**
     * Marks all unread notifications for a user as read (bulk read-all).
     *
     * @return number of records updated
     */
    @Transactional
    public int markAllRead(Long userId) {
        int count = notificationRepository.markAllReadByUser(userId, Instant.now());
        log.info("NOTIFICATION_MARK_READ markAllRead userId: {}, count: {}", userId, count);
        return count;
    }

    /**
     * Returns a page of notifications for the user, newest first.
     */
    @Transactional(readOnly = true)
    public Page<UserNotification> list(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * Returns the number of unread notifications for the user.
     * Backed by (user_id, is_read) index for efficiency.
     */
    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        long count = notificationRepository.countByUserIdAndIsReadFalse(userId);
        log.debug("NOTIFICATION_UNREAD_COUNT userId: {}, count: {}", userId, count);
        return count;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private UserNotification build(Long userId, NotificationType type,
                                   String title, String message,
                                   Long ticketId, UUID ticketPublicId, String ticketNo,
                                   Long groupId, Long actorUserId) {
        UserNotification n = new UserNotification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setTicketId(ticketId);
        n.setTicketPublicId(ticketPublicId);
        n.setTicketNo(ticketNo);
        n.setGroupId(groupId);
        n.setActorUserId(actorUserId);
        return n;
    }
}
