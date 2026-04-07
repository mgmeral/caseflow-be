package com.caseflow.integration.notification.service;

import com.caseflow.integration.domain.IntegrationJob;
import com.caseflow.integration.domain.IntegrationJobStatus;
import com.caseflow.integration.domain.IntegrationType;
import com.caseflow.integration.domain.TicketDomainEvent;
import com.caseflow.integration.notification.domain.ChannelType;
import com.caseflow.integration.notification.domain.NotificationChannelConfig;
import com.caseflow.integration.notification.domain.NotificationEventType;
import com.caseflow.integration.service.IntegrationJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalNotificationServiceTest {

    @Mock private NotificationChannelConfigService configService;
    @Mock private IntegrationJobService jobService;

    @InjectMocks
    private ExternalNotificationService service;

    private IntegrationJob enqueuedJob;

    @BeforeEach
    void setUp() {
        enqueuedJob = new IntegrationJob();
        enqueuedJob.setStatus(IntegrationJobStatus.PENDING);
        lenient().when(jobService.enqueue(any(), any(), any(), any(), any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(enqueuedJob);
    }

    @Test
    void onTicketDomainEvent_createsJob_forMatchingGlobalSlackConfig() {
        NotificationChannelConfig config = buildSlackConfig("GLOBAL", null,
                "[\"TICKET_CREATED\"]");
        when(configService.findEnabled()).thenReturn(List.of(config));

        TicketDomainEvent event = new TicketDomainEvent(1L, UUID.randomUUID(),
                NotificationEventType.TICKET_CREATED, 42L, null, null);

        service.onTicketDomainEvent(event);

        verify(jobService).enqueue(
                eq(IntegrationType.SLACK_NOTIFICATION),
                eq(1L), any(), any(), any(), anyString(), anyString(), eq("EVENT"), eq(42L));
    }

    @Test
    void onTicketDomainEvent_skipsJob_whenEventNotSubscribed() {
        NotificationChannelConfig config = buildSlackConfig("GLOBAL", null,
                "[\"TICKET_CLOSED\"]");
        when(configService.findEnabled()).thenReturn(List.of(config));

        TicketDomainEvent event = new TicketDomainEvent(1L, UUID.randomUUID(),
                NotificationEventType.TICKET_CREATED, 42L, null, null);

        service.onTicketDomainEvent(event);

        verify(jobService, never()).enqueue(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void onTicketDomainEvent_skipsJob_whenScopeDoesNotMatch() {
        NotificationChannelConfig config = buildSlackConfig("CUSTOMER", 99L,
                "[\"TICKET_CREATED\"]");
        when(configService.findEnabled()).thenReturn(List.of(config));

        // Event has customerId=10, config is scoped to customer 99
        TicketDomainEvent event = new TicketDomainEvent(1L, UUID.randomUUID(),
                NotificationEventType.TICKET_CREATED, 42L, 10L, null);

        service.onTicketDomainEvent(event);

        verify(jobService, never()).enqueue(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void onTicketDomainEvent_matchesCustomerScope_whenCustomerIdMatches() {
        NotificationChannelConfig config = buildSlackConfig("CUSTOMER", 10L,
                "[\"TICKET_CREATED\"]");
        when(configService.findEnabled()).thenReturn(List.of(config));

        TicketDomainEvent event = new TicketDomainEvent(1L, UUID.randomUUID(),
                NotificationEventType.TICKET_CREATED, 42L, 10L, null);

        service.onTicketDomainEvent(event);

        verify(jobService).enqueue(
                eq(IntegrationType.SLACK_NOTIFICATION),
                eq(1L), any(), any(), any(), anyString(), anyString(), eq("EVENT"), eq(42L));
    }

    @Test
    void onTicketDomainEvent_createsMultipleJobs_forMultipleConfigs() {
        NotificationChannelConfig slack = buildSlackConfig("GLOBAL", null,
                "[\"TICKET_RESOLVED\"]");
        NotificationChannelConfig teams = buildTeamsConfig("GLOBAL", null,
                "[\"TICKET_RESOLVED\"]");
        when(configService.findEnabled()).thenReturn(List.of(slack, teams));

        TicketDomainEvent event = new TicketDomainEvent(1L, UUID.randomUUID(),
                NotificationEventType.TICKET_RESOLVED, 42L, null, null);

        service.onTicketDomainEvent(event);

        verify(jobService, times(2)).enqueue(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void onTicketDomainEvent_doesNothing_whenNoConfigsEnabled() {
        when(configService.findEnabled()).thenReturn(List.of());

        TicketDomainEvent event = new TicketDomainEvent(1L, UUID.randomUUID(),
                NotificationEventType.TICKET_CREATED, 42L, null, null);

        service.onTicketDomainEvent(event);

        verify(jobService, never()).enqueue(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private NotificationChannelConfig buildSlackConfig(String scopeType, Long scopeId,
                                                        String subscribedEvents) {
        NotificationChannelConfig c = new NotificationChannelConfig();
        c.setChannelType(ChannelType.SLACK);
        c.setIsEnabled(true);
        c.setWebhookUrl("https://hooks.slack.com/test");
        c.setScopeType(scopeType);
        c.setScopeId(scopeId);
        c.setSubscribedEvents(subscribedEvents);
        return c;
    }

    private NotificationChannelConfig buildTeamsConfig(String scopeType, Long scopeId,
                                                        String subscribedEvents) {
        NotificationChannelConfig c = new NotificationChannelConfig();
        c.setChannelType(ChannelType.TEAMS);
        c.setIsEnabled(true);
        c.setWebhookUrl("https://outlook.office.com/webhook/test");
        c.setScopeType(scopeType);
        c.setScopeId(scopeId);
        c.setSubscribedEvents(subscribedEvents);
        return c;
    }
}
