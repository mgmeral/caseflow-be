package com.caseflow.integration.scheduler;

import com.caseflow.integration.domain.IntegrationJob;
import com.caseflow.integration.domain.IntegrationJobStatus;
import com.caseflow.integration.domain.IntegrationType;
import com.caseflow.integration.service.IntegrationJobClaimService;
import com.caseflow.integration.service.IntegrationJobExecutionException;
import com.caseflow.integration.service.IntegrationJobProcessor;
import com.caseflow.integration.service.IntegrationJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationJobWorkerTest {

    @Mock private IntegrationJobClaimService claimService;
    @Mock private IntegrationJobService jobService;
    @Mock private IntegrationJobProcessor jiraProcessor;

    private IntegrationJobWorker worker;

    @BeforeEach
    void setUp() {
        when(jiraProcessor.supportedType()).thenReturn(IntegrationType.JIRA_ISSUE_CREATE);
        worker = new IntegrationJobWorker(claimService, jobService, List.of(jiraProcessor));
    }

    // ── processPending ────────────────────────────────────────────────────────

    @Test
    void processPending_delegatesClaimToClaimService() {
        when(claimService.claimPendingBatch(anyInt())).thenReturn(List.of());

        worker.processPending();

        verify(claimService).claimPendingBatch(10);
    }

    @Test
    void processPending_processesEachClaimedJob() throws IntegrationJobExecutionException {
        IntegrationJob job = buildJob(IntegrationType.JIRA_ISSUE_CREATE);
        when(claimService.claimPendingBatch(anyInt())).thenReturn(List.of(job));

        worker.processPending();

        verify(jiraProcessor).process(job);
    }

    @Test
    void processPending_doesNotCallProcessor_whenBatchEmpty() throws IntegrationJobExecutionException {
        when(claimService.claimPendingBatch(anyInt())).thenReturn(List.of());

        worker.processPending();

        verify(jiraProcessor, never()).process(any());
    }

    // ── retryFailed ───────────────────────────────────────────────────────────

    @Test
    void retryFailed_delegatesClaimToClaimService() {
        when(claimService.claimRetryBatch(anyInt())).thenReturn(List.of());

        worker.retryFailed();

        verify(claimService).claimRetryBatch(10);
    }

    @Test
    void retryFailed_processesEachClaimedJob() throws IntegrationJobExecutionException {
        IntegrationJob job = buildJob(IntegrationType.JIRA_ISSUE_CREATE);
        when(claimService.claimRetryBatch(anyInt())).thenReturn(List.of(job));

        worker.retryFailed();

        verify(jiraProcessor).process(job);
    }

    @Test
    void retryFailed_doesNotCallProcessor_whenBatchEmpty() throws IntegrationJobExecutionException {
        when(claimService.claimRetryBatch(anyInt())).thenReturn(List.of());

        worker.retryFailed();

        verify(jiraProcessor, never()).process(any());
    }

    // ── processJob ────────────────────────────────────────────────────────────

    @Test
    void processJob_callsMatchingProcessor() throws IntegrationJobExecutionException {
        IntegrationJob job = buildJob(IntegrationType.JIRA_ISSUE_CREATE);

        worker.processJob(job);

        verify(jiraProcessor).process(job);
    }

    @Test
    void processJob_marksPermanentlyFailed_whenNoProcessorRegistered() {
        IntegrationJob job = buildJob(IntegrationType.SLACK_NOTIFICATION);

        worker.processJob(job);

        verify(jobService).markPermanentlyFailed(any(), anyString());
        verify(jiraProcessor, never()).process(any());
    }

    @Test
    void processJob_marksFailed_onTransientException() throws IntegrationJobExecutionException {
        IntegrationJob job = buildJob(IntegrationType.JIRA_ISSUE_CREATE);
        doThrow(new IntegrationJobExecutionException("timeout", false))
                .when(jiraProcessor).process(job);

        worker.processJob(job);

        verify(jobService).markFailed(job, "timeout");
    }

    @Test
    void processJob_marksPermanentlyFailed_onPermanentException() throws IntegrationJobExecutionException {
        IntegrationJob job = buildJob(IntegrationType.JIRA_ISSUE_CREATE);
        doThrow(new IntegrationJobExecutionException("401 Unauthorized", true))
                .when(jiraProcessor).process(job);

        worker.processJob(job);

        verify(jobService).markPermanentlyFailed(job, "401 Unauthorized");
    }

    @Test
    void processJob_marksFailed_onUnexpectedException() throws IntegrationJobExecutionException {
        IntegrationJob job = buildJob(IntegrationType.JIRA_ISSUE_CREATE);
        doThrow(new RuntimeException("NPE"))
                .when(jiraProcessor).process(job);

        worker.processJob(job);

        verify(jobService).markFailed(any(), anyString());
    }

    private IntegrationJob buildJob(IntegrationType type) {
        IntegrationJob job = new IntegrationJob();
        job.setIntegrationType(type);
        job.setStatus(IntegrationJobStatus.PROCESSING);
        job.setAttemptCount(1);
        return job;
    }
}
