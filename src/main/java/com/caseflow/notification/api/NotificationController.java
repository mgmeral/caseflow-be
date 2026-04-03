package com.caseflow.notification.api;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.common.api.PagedResponse;
import com.caseflow.notification.api.dto.NotificationResponse;
import com.caseflow.notification.api.dto.UnreadCountResponse;
import com.caseflow.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notifications", description = "Per-user ticket notifications and unread state")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("hasAuthority('PERM_TICKET_READ')")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Returns a pageable list of notifications for the current user, newest first.
     */
    @GetMapping
    public ResponseEntity<PagedResponse<NotificationResponse>> list(
            @AuthenticationPrincipal CaseFlowUserDetails user,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /notifications — userId: {}", user.getUserId());
        PageRequest pr = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(PagedResponse.from(
                notificationService.list(user.getUserId(), pr)
                        .map(NotificationResponse::from)));
    }

    /**
     * Returns the number of unread notifications for the current user.
     * Efficient single-query endpoint — suitable for polling from a notification bell.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> unreadCount(
            @AuthenticationPrincipal CaseFlowUserDetails user) {
        long count = notificationService.unreadCount(user.getUserId());
        return ResponseEntity.ok(new UnreadCountResponse(count));
    }

    /**
     * Marks a single notification as read for the current user.
     * Returns 404 if the notification is not found or does not belong to the user.
     */
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id,
                                          @AuthenticationPrincipal CaseFlowUserDetails user) {
        log.info("POST /notifications/{}/read — userId: {}", id, user.getUserId());
        boolean updated = notificationService.markReadById(user.getUserId(), id);
        return updated
                ? ResponseEntity.ok().<Void>build()
                : ResponseEntity.notFound().build();
    }

    /**
     * Marks all unread notifications as read for the current user.
     */
    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal CaseFlowUserDetails user) {
        log.info("POST /notifications/read-all — userId: {}", user.getUserId());
        notificationService.markAllRead(user.getUserId());
        return ResponseEntity.ok().build();
    }
}
