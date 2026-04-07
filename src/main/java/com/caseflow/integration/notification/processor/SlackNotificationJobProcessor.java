package com.caseflow.integration.notification.processor;

import com.caseflow.integration.domain.IntegrationJob;
import com.caseflow.integration.domain.IntegrationType;
import com.caseflow.integration.notification.domain.NotificationChannelConfig;
import com.caseflow.integration.notification.repository.NotificationChannelConfigRepository;
import com.caseflow.integration.service.IntegrationJobService;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Processes {@link IntegrationType#SLACK_NOTIFICATION} jobs.
 *
 * <p>Formats a Slack Block Kit message and POSTs it to the configured incoming webhook URL.
 */
@Component
public class SlackNotificationJobProcessor extends WebhookNotificationJobProcessor {

    public SlackNotificationJobProcessor(
            NotificationChannelConfigRepository configRepository,
            TicketRepository ticketRepository,
            IntegrationJobService jobService,
            TicketHistoryService historyService,
            RestTemplate restTemplate) {
        super(configRepository, ticketRepository, jobService, historyService, restTemplate);
    }

    @Override
    public IntegrationType supportedType() {
        return IntegrationType.SLACK_NOTIFICATION;
    }

    @Override
    protected String buildWebhookPayload(NotificationChannelConfig config,
                                          String eventType,
                                          Ticket ticket,
                                          IntegrationJob job) {
        String text = buildText(eventType, ticket);
        return "{\"text\":\"" + escapeJson(text) + "\","
                + "\"blocks\":[{\"type\":\"section\","
                + "\"text\":{\"type\":\"mrkdwn\","
                + "\"text\":\"" + escapeJson(text) + "\"}}]}";
    }

    private String buildText(String eventType, Ticket ticket) {
        String emoji = switch (eventType) {
            case "TICKET_CREATED" -> ":new:";
            case "TICKET_ASSIGNED" -> ":bust_in_silhouette:";
            case "TICKET_TRANSFERRED" -> ":arrows_counterclockwise:";
            case "TICKET_RESOLVED" -> ":white_check_mark:";
            case "TICKET_CLOSED" -> ":lock:";
            case "OUTBOUND_REPLY_FAILED" -> ":warning:";
            default -> ":bell:";
        };

        if (ticket == null) {
            return emoji + " CaseFlow event: " + eventType;
        }

        return String.format("%s *%s* — %s\n*Ticket:* `%s` — %s\n*Status:* %s | *Priority:* %s",
                emoji,
                formatEventType(eventType),
                ticket.getTicketNo(),
                ticket.getTicketNo(),
                truncate(ticket.getSubject(), 120),
                ticket.getStatus(),
                ticket.getPriority()
        );
    }

    private static String formatEventType(String eventType) {
        return eventType.replace("_", " ").toLowerCase()
                .substring(0, 1).toUpperCase()
                + eventType.replace("_", " ").toLowerCase().substring(1);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
