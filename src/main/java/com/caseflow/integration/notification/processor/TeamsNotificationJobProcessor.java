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
 * Processes {@link IntegrationType#TEAMS_NOTIFICATION} jobs.
 *
 * <p>Formats a Microsoft Teams Adaptive Card payload and POSTs it to the
 * configured incoming webhook URL.
 */
@Component
public class TeamsNotificationJobProcessor extends WebhookNotificationJobProcessor {

    public TeamsNotificationJobProcessor(
            NotificationChannelConfigRepository configRepository,
            TicketRepository ticketRepository,
            IntegrationJobService jobService,
            TicketHistoryService historyService,
            RestTemplate restTemplate) {
        super(configRepository, ticketRepository, jobService, historyService, restTemplate);
    }

    @Override
    public IntegrationType supportedType() {
        return IntegrationType.TEAMS_NOTIFICATION;
    }

    @Override
    protected String buildWebhookPayload(NotificationChannelConfig config,
                                          String eventType,
                                          Ticket ticket,
                                          IntegrationJob job) {
        String title = formatEventType(eventType);
        String text;
        if (ticket == null) {
            text = "CaseFlow event: " + eventType;
        } else {
            text = String.format("[%s] %s\nStatus: %s | Priority: %s",
                    ticket.getTicketNo(),
                    truncate(ticket.getSubject(), 120),
                    ticket.getStatus(),
                    ticket.getPriority()
            );
        }

        // Teams MessageCard format (compatible with most webhook configs)
        return "{\"@type\":\"MessageCard\","
                + "\"@context\":\"http://schema.org/extensions\","
                + "\"themeColor\":\"0076D7\","
                + "\"summary\":\"" + escapeJson(title) + "\","
                + "\"sections\":[{\"activityTitle\":\"" + escapeJson(title) + "\","
                + "\"activityText\":\"" + escapeJson(text) + "\"}]}";
    }

    private static String formatEventType(String eventType) {
        String[] words = eventType.split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(word.charAt(0)).append(word.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
