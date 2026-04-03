package com.caseflow.notification.repository;

import com.caseflow.notification.domain.NotificationType;
import com.caseflow.notification.domain.UserNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

    /** Pageable list of all notifications for a user, newest first. */
    Page<UserNotification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Efficient unread count — backed by (user_id, is_read) index. */
    long countByUserIdAndIsReadFalse(Long userId);

    /**
     * Returns true if the user already has an unread notification of the given type
     * for the given ticket — used to prevent duplicate notifications on no-op reassignments.
     */
    boolean existsByUserIdAndTicketIdAndTypeAndIsReadFalse(Long userId, Long ticketId, NotificationType type);

    /**
     * Bulk-marks all unread notifications for the given user+ticket as read.
     * Called when a user opens the ticket detail view.
     */
    @Modifying
    @Query("""
            UPDATE UserNotification n
            SET n.isRead = true, n.readAt = :now
            WHERE n.userId = :userId AND n.ticketId = :ticketId AND n.isRead = false
            """)
    int markReadByUserAndTicket(@Param("userId") Long userId,
                                @Param("ticketId") Long ticketId,
                                @Param("now") Instant now);

    /**
     * Marks a single notification as read, verifying ownership via userId.
     */
    @Modifying
    @Query("""
            UPDATE UserNotification n
            SET n.isRead = true, n.readAt = :now
            WHERE n.id = :id AND n.userId = :userId AND n.isRead = false
            """)
    int markReadByIdAndUser(@Param("id") Long id,
                            @Param("userId") Long userId,
                            @Param("now") Instant now);

    /**
     * Marks all unread notifications for a user as read.
     */
    @Modifying
    @Query("""
            UPDATE UserNotification n
            SET n.isRead = true, n.readAt = :now
            WHERE n.userId = :userId AND n.isRead = false
            """)
    int markAllReadByUser(@Param("userId") Long userId, @Param("now") Instant now);
}
