package com.caseflow.integration.notification.service;

import com.caseflow.integration.domain.IntegrationJob;
import com.caseflow.integration.domain.IntegrationType;
import com.caseflow.integration.domain.TicketDomainEvent;
import com.caseflow.integration.notification.domain.ChannelType;
import com.caseflow.integration.notification.domain.NotificationChannelConfig;
import com.caseflow.integration.notification.domain.NotificationEventType;
import com.caseflow.integration.service.IntegrationJobService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

/**
 * Listens for {@link TicketDomainEvent}s and creates durable
 * {@link IntegrationJob}s for each matching notification channel config.
 *
 * <p>Uses {@code AFTER_COMMIT} so we never queue notifications for transactions
 * that roll back. Each matching config gets its own job to allow independent
 * success/failure tracking.
 */
@Service
public class ExternalNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ExternalNotificationService.class);

    private final NotificationChannelConfigService configService;
    private final IntegrationJobService jobService;

    public ExternalNotificationService(NotificationChannelConfigService configService,
                                        IntegrationJobService jobService) {
        this.configService = configService;
        this.jobService = jobService;
    }

    /**
     * Called after the originating ticket transaction commits.
     * Enqueues one integration job per matching channel config.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTicketDomainEvent(TicketDomainEvent event) {
        List<NotificationChannelConfig> candidates = configService.findEnabled();
        if (candidates.isEmpty()) return;

        for (NotificationChannelConfig config : candidates) {
            if (!subscribedTo(config, event.getEventType())) continue;
            if (!matchesScope(config, event)) continue;

            enqueueNotificationJob(config, event);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void enqueueNotificationJob(NotificationChannelConfig config, TicketDomainEvent event) {
        IntegrationType type = config.getChannelType() == ChannelType.SLACK
                ? IntegrationType.SLACK_NOTIFICATION
                : IntegrationType.TEAMS_NOTIFICATION;

        String payload = buildPayloadJson(config, event);

        // Idempotency key: config + event + ticket (prevents duplicate jobs for same event on same channel)
        String idempotencyKey = type.name() + ":" + config.getId() + ":" + event.getTicketId()
                + ":" + event.getEventType().name();

        try {
            IntegrationJob job = jobService.enqueue(
                    type,
                    event.getTicketId(),
                    event.getTicketPublicId(),
                    event.getCustomerId(),
                    null,
                    payload,
                    idempotencyKey,
                    "EVENT",
                    event.getActorId()
            );
            log.debug("Notification job enqueued — type: {}, channel: {}, ticket: {}, jobId: {}",
                    type, config.getName(), event.getTicketId(), job.getId());
        } catch (Exception e) {
            // Don't fail the AFTER_COMMIT listener for idempotency collisions or other errors
            log.warn("Failed to enqueue notification job for config {}: {}", config.getId(), e.getMessage());
        }
    }

    private boolean subscribedTo(NotificationChannelConfig config, NotificationEventType eventType) {
        String events = config.getSubscribedEvents();
        if (events == null || events.equals("[]")) return false;
        return events.contains("\"" + eventType.name() + "\"");
    }

    private boolean matchesScope(NotificationChannelConfig config, TicketDomainEvent event) {
        return switch (config.getScopeType()) {
            case "GLOBAL" -> true;
            case "GROUP" -> config.getScopeId() != null
                    && config.getScopeId().equals(event.getAssignedGroupId());
            case "CUSTOMER" -> config.getScopeId() != null
                    && config.getScopeId().equals(event.getCustomerId());
            default -> true;
        };
    }

    private String buildPayloadJson(NotificationChannelConfig config, TicketDomainEvent event) {
        // Simple JSON with channel config id and event metadata
        return "{\"channelConfigId\":" + config.getId()
                + ",\"channelType\":\"" + config.getChannelType().name() + "\""
                + ",\"eventType\":\"" + event.getEventType().name() + "\""
                + ",\"ticketId\":" + event.getTicketId()
                + ",\"ticketPublicId\":\"" + event.getTicketPublicId() + "\""
                + (event.getCustomerId() != null ? ",\"customerId\":" + event.getCustomerId() : "")
                + (event.getAssignedGroupId() != null ? ",\"groupId\":" + event.getAssignedGroupId() : "")
                + (event.getActorId() != null ? ",\"actorId\":" + event.getActorId() : "")
                + "}";
    }
}
