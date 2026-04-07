package com.caseflow.integration.jira.processor;

import com.caseflow.customer.repository.CustomerRepository;
import com.caseflow.integration.domain.IntegrationJob;
import com.caseflow.integration.domain.IntegrationJobStatus;
import com.caseflow.integration.domain.IntegrationType;
import com.caseflow.integration.jira.domain.JiraConfig;
import com.caseflow.integration.jira.domain.TicketJiraLink;
import com.caseflow.integration.jira.repository.TicketJiraLinkRepository;
import com.caseflow.integration.jira.service.JiraApiClient;
import com.caseflow.integration.jira.service.JiraConfigService;
import com.caseflow.integration.service.IntegrationJobExecutionException;
import com.caseflow.integration.service.IntegrationJobService;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JiraJobProcessorTest {

    @Mock private JiraConfigService configService;
    @Mock private JiraApiClient jiraApiClient;
    @Mock private TicketRepository ticketRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private TicketJiraLinkRepository linkRepository;
    @Mock private IntegrationJobService jobService;
    @Mock private TicketHistoryService historyService;

    @InjectMocks
    private JiraJobProcessor processor;

    private IntegrationJob job;
    private Ticket ticket;
    private JiraConfig config;

    @BeforeEach
    void setUp() {
        ticket = new Ticket();
        ticket.setTicketNo("TKT-0000001");
        ticket.setSubject("Test issue");
        ticket.setStatus(TicketStatus.NEW);
        ticket.setPriority(TicketPriority.HIGH);

        config = new JiraConfig();
        config.setIsEnabled(true);
        config.setBaseUrl("https://jira.example.com");
        config.setProjectKey("TEST");
        config.setIssueType("Task");

        job = new IntegrationJob();
        job.setIntegrationType(IntegrationType.JIRA_ISSUE_CREATE);
        job.setStatus(IntegrationJobStatus.PROCESSING);
        job.setAttemptCount(1);
        job.setTicketId(1L);
        job.setCreatedBy(42L);
    }

    @Test
    void process_createsLink_andMarksSucceeded_onSuccess() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(linkRepository.existsByTicketId(any())).thenReturn(false);
        when(configService.requireEnabledConfig()).thenReturn(config);
        when(linkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jiraApiClient.createIssue(any(), any()))
                .thenReturn(new JiraApiClient.JiraIssueResult("TEST-42", "10001",
                        "https://jira.example.com/browse/TEST-42"));

        processor.process(job);

        ArgumentCaptor<TicketJiraLink> linkCaptor = ArgumentCaptor.forClass(TicketJiraLink.class);
        verify(linkRepository).save(linkCaptor.capture());
        assertThat(linkCaptor.getValue().getJiraIssueKey()).isEqualTo("TEST-42");
        assertThat(linkCaptor.getValue().getJiraUrl()).contains("TEST-42");

        verify(jobService).markSucceeded(eq(job), eq("TEST-42"));
        verify(historyService).recordJiraIssueCreated(any(), any(), any(), eq("TEST-42"), anyString());
    }

    @Test
    void process_throwsAndRecordsFailure_whenJiraCallFails() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(linkRepository.existsByTicketId(any())).thenReturn(false);
        when(configService.requireEnabledConfig()).thenReturn(config);
        when(jiraApiClient.createIssue(any(), any()))
                .thenThrow(new IntegrationJobExecutionException("403 Forbidden", true));

        assertThatThrownBy(() -> processor.process(job))
                .isInstanceOf(IntegrationJobExecutionException.class)
                .hasMessageContaining("403");

        verify(historyService).recordJiraCreateFailed(any(), any(), any(), any(), eq(true));
        verify(linkRepository, never()).save(any());
    }

    @Test
    void process_throwsPermanent_whenTicketNotFound() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> processor.process(job))
                .isInstanceOf(IntegrationJobExecutionException.class)
                .matches(e -> ((IntegrationJobExecutionException) e).isPermanent());
    }

    @Test
    void process_marksSucceeded_withExistingLink_asIdempotency() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(linkRepository.existsByTicketId(any())).thenReturn(true);
        TicketJiraLink existingLink = new TicketJiraLink();
        existingLink.setJiraIssueKey("TEST-1");
        when(linkRepository.findByTicketId(any())).thenReturn(Optional.of(existingLink));

        processor.process(job);

        verify(jiraApiClient, never()).createIssue(any(), any());
        verify(jobService).markSucceeded(eq(job), eq("TEST-1"));
    }

    private static String anyString() {
        return org.mockito.ArgumentMatchers.anyString();
    }
}
