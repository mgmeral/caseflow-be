package com.caseflow.integration.notification.processor;

import com.caseflow.integration.domain.IntegrationJob;
import com.caseflow.integration.domain.IntegrationType;
import com.caseflow.integration.notification.domain.NotificationChannelConfig;
import com.caseflow.integration.notification.repository.NotificationChannelConfigRepository;
import com.caseflow.integration.service.IntegrationJobExecutionException;
import com.caseflow.integration.service.IntegrationJobProcessor;
import com.caseflow.integration.service.IntegrationJobService;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared Slack/Teams webhook notification processor.
 *
 * <p>Subclasses specify the {@link IntegrationType} and the message payload format.
 */
abstract class WebhookNotificationJobProcessor implements IntegrationJobProcessor {

    private static final Logger log = LoggerFactory.getLogger(WebhookNotificationJobProcessor.class);

    private static final Pattern CHANNEL_CONFIG_ID_PATTERN =
            Pattern.compile("\"channelConfigId\":(\\d+)");
    private static final Pattern EVENT_TYPE_PATTERN =
            Pattern.compile("\"eventType\":\"([^\"]+)\"");
    private static final Pattern TICKET_PUBLIC_ID_PATTERN =
            Pattern.compile("\"ticketPublicId\":\"([^\"]+)\"");

    private final NotificationChannelConfigRepository configRepository;
    private final TicketRepository ticketRepository;
    private final IntegrationJobService jobService;
    private final TicketHistoryService historyService;
    private final RestTemplate restTemplate;

    protected WebhookNotificationJobProcessor(
            NotificationChannelConfigRepository configRepository,
            TicketRepository ticketRepository,
            IntegrationJobService jobService,
            TicketHistoryService historyService,
            RestTemplate restTemplate) {
        this.configRepository = configRepository;
        this.ticketRepository = ticketRepository;
        this.jobService = jobService;
        this.historyService = historyService;
        this.restTemplate = restTemplate;
    }

    @Override
    @Transactional
    public void process(IntegrationJob job) throws IntegrationJobExecutionException {
        String payload = job.getPayloadJson();
        if (payload == null) {
            throw new IntegrationJobExecutionException("Missing payload JSON", true);
        }

        long configId = extractLong(payload, CHANNEL_CONFIG_ID_PATTERN);
        String eventType = extractString(payload, EVENT_TYPE_PATTERN);
        String ticketPublicIdStr = extractString(payload, TICKET_PUBLIC_ID_PATTERN);

        NotificationChannelConfig config = configRepository.findById(configId)
                .orElseThrow(() -> new IntegrationJobExecutionException(
                        "Channel config " + configId + " not found", true));

        if (!Boolean.TRUE.equals(config.getIsEnabled())) {
            throw new IntegrationJobExecutionException(
                    "Channel config " + configId + " is disabled", true);
        }

        // Load ticket for message content
        Ticket ticket = null;
        if (job.getTicketId() != null) {
            ticket = ticketRepository.findById(job.getTicketId()).orElse(null);
        }

        String webhookPayload = buildWebhookPayload(config, eventType, ticket, job);
        try {
            sendWebhook(config.getWebhookUrl(), webhookPayload);
        } catch (IntegrationJobExecutionException e) {
            // Record failure history before re-throwing
            if (job.getTicketId() != null && ticket != null) {
                historyService.recordExternalNotificationFailed(
                        job.getTicketId(), ticket.getPublicId(),
                        job.getId(), config.getChannelType().name(), config.getName(), e.getMessage());
            }
            throw e;
        }

        jobService.markSucceeded(job, "SENT");

        // Record history on success
        if (job.getTicketId() != null && ticket != null) {
            historyService.recordExternalNotificationSent(
                    job.getTicketId(), ticket.getPublicId(),
                    job.getId(), config.getChannelType().name(), config.getName(), eventType);
        }

        log.info("Webhook notification sent — config: {} ({}), event: {}, ticket: {}",
                config.getName(), config.getChannelType(), eventType, job.getTicketId());
    }

    private void sendWebhook(String webhookUrl, String jsonPayload)
            throws IntegrationJobExecutionException {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            ResponseEntity<String> response = restTemplate.exchange(
                    webhookUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(jsonPayload, headers),
                    String.class
            );

            int statusCode = response.getStatusCode().value();
            // Slack returns "ok", Teams returns 1 or 200 body
            if (statusCode >= 400) {
                throw new IntegrationJobExecutionException(
                        "Webhook returned HTTP " + statusCode, statusCode < 500);
            }
        } catch (HttpClientErrorException e) {
            // 4xx = permanent (bad URL, auth), 5xx = transient
            throw new IntegrationJobExecutionException(
                    "Webhook error " + e.getStatusCode() + ": " + truncate(e.getResponseBodyAsString(), 200),
                    e.getStatusCode().is4xxClientError(),
                    e);
        } catch (IntegrationJobExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new IntegrationJobExecutionException(
                    "Webhook call failed: " + e.getMessage(), false, e);
        }
    }

    /** Build the channel-type-specific webhook JSON payload. */
    protected abstract String buildWebhookPayload(NotificationChannelConfig config,
                                                   String eventType,
                                                   Ticket ticket,
                                                   IntegrationJob job);

    // ── Helpers ───────────────────────────────────────────────────────────────

    protected static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static long extractLong(String json, Pattern pattern) {
        Matcher m = pattern.matcher(json);
        if (!m.find()) throw new IllegalStateException("Pattern not found: " + pattern.pattern());
        return Long.parseLong(m.group(1));
    }

    private static String extractString(String json, Pattern pattern) {
        Matcher m = pattern.matcher(json);
        return m.find() ? m.group(1) : "";
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }
}
