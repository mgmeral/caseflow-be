package com.caseflow.integration.jira.service;

import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.integration.domain.IntegrationJob;
import com.caseflow.integration.domain.IntegrationJobStatus;
import com.caseflow.integration.domain.IntegrationType;
import com.caseflow.integration.jira.domain.JiraConfig;
import com.caseflow.integration.jira.repository.TicketJiraLinkRepository;
import com.caseflow.integration.service.IntegrationJobService;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JiraIntegrationServiceTest {

    @Mock private IntegrationJobService jobService;
    @Mock private JiraConfigService jiraConfigService;
    @Mock private TicketJiraLinkRepository linkRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private TicketHistoryService historyService;

    @InjectMocks
    private JiraIntegrationService service;

    private Ticket ticket;
    private JiraConfig config;
    private IntegrationJob job;

    @BeforeEach
    void setUp() {
        ticket = new Ticket();
        // Reflectively set id via TestUtils or just let public_id work
        ticket.setTicketNo("TKT-0000001");
        ticket.setSubject("Test subject");
        ticket.setStatus(TicketStatus.NEW);
        ticket.setPriority(TicketPriority.MEDIUM);

        config = new JiraConfig();
        config.setIsEnabled(true);
        config.setBaseUrl("https://jira.example.com");
        config.setProjectKey("TEST");
        config.setIssueType("Task");

        job = new IntegrationJob();
        job.setIntegrationType(IntegrationType.JIRA_ISSUE_CREATE);
        job.setStatus(IntegrationJobStatus.PENDING);
        job.setAttemptCount(0);
    }

    @Test
    void requestJiraCreate_throwsTicketNotFound_whenTicketMissing() {
        UUID publicId = UUID.randomUUID();
        when(ticketRepository.findByPublicId(publicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestJiraCreate(publicId, 1L))
                .isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void requestJiraCreate_throwsIllegalState_whenLinkAlreadyExists() {
        UUID publicId = UUID.randomUUID();
        when(ticketRepository.findByPublicId(publicId)).thenReturn(Optional.of(ticket));
        when(linkRepository.existsByTicketId(any())).thenReturn(true);

        assertThatThrownBy(() -> service.requestJiraCreate(publicId, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already has a Jira issue");
    }

    @Test
    void requestJiraCreate_throwsIllegalState_whenActiveJobExists() {
        UUID publicId = UUID.randomUUID();
        when(ticketRepository.findByPublicId(publicId)).thenReturn(Optional.of(ticket));
        when(linkRepository.existsByTicketId(any())).thenReturn(false);
        when(jobService.hasActiveJob(any(), eq(IntegrationType.JIRA_ISSUE_CREATE))).thenReturn(true);

        assertThatThrownBy(() -> service.requestJiraCreate(publicId, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already pending");
    }

    @Test
    void requestJiraCreate_enqueuesJob_whenNoConflict() {
        UUID publicId = UUID.randomUUID();
        when(ticketRepository.findByPublicId(publicId)).thenReturn(Optional.of(ticket));
        when(linkRepository.existsByTicketId(any())).thenReturn(false);
        when(jobService.hasActiveJob(any(), any())).thenReturn(false);
        when(jiraConfigService.requireEnabledConfig()).thenReturn(config);
        when(jobService.enqueue(any(), any(), any(), any(), any(), any(), anyString(), anyString(), anyLong()))
                .thenReturn(job);

        IntegrationJob result = service.requestJiraCreate(publicId, 42L);

        assertThat(result).isSameAs(job);
        verify(historyService).recordJiraCreateRequested(any(), any(), any(), eq(42L));
    }

    @Test
    void requestJiraCreate_throwsIllegalState_whenJiraDisabled() {
        UUID publicId = UUID.randomUUID();
        when(ticketRepository.findByPublicId(publicId)).thenReturn(Optional.of(ticket));
        when(linkRepository.existsByTicketId(any())).thenReturn(false);
        when(jobService.hasActiveJob(any(), any())).thenReturn(false);
        when(jiraConfigService.requireEnabledConfig())
                .thenThrow(new IllegalStateException("Jira integration is disabled"));

        assertThatThrownBy(() -> service.requestJiraCreate(publicId, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }
}
