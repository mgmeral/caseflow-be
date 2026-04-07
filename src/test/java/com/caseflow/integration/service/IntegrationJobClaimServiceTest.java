package com.caseflow.integration.service;

import com.caseflow.integration.domain.IntegrationJob;
import com.caseflow.integration.domain.IntegrationJobStatus;
import com.caseflow.integration.domain.IntegrationType;
import com.caseflow.integration.repository.IntegrationJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationJobClaimServiceTest {

    @Mock private IntegrationJobRepository jobRepository;

    private IntegrationJobClaimService claimService;

    @BeforeEach
    void setUp() {
        claimService = new IntegrationJobClaimService(jobRepository);
    }

    // ── claimPendingBatch ─────────────────────────────────────────────────────

    @Test
    void claimPendingBatch_queriesRepositoryWithCorrectLimit() {
        when(jobRepository.claimPendingBatch(any(Instant.class), eq(5))).thenReturn(List.of());

        claimService.claimPendingBatch(5);

        verify(jobRepository).claimPendingBatch(any(Instant.class), eq(5));
    }

    @Test
    void claimPendingBatch_setsStatusToProcessingAndIncrementsAttemptCount() {
        IntegrationJob job = pendingJob();
        when(jobRepository.claimPendingBatch(any(Instant.class), eq(10)))
                .thenReturn(List.of(job));

        List<IntegrationJob> result = claimService.claimPendingBatch(10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(IntegrationJobStatus.PROCESSING);
        assertThat(result.get(0).getAttemptCount()).isEqualTo(1); // was 0
    }

    @Test
    void claimPendingBatch_returnsEmptyList_whenNoPendingJobs() {
        when(jobRepository.claimPendingBatch(any(Instant.class), eq(10)))
                .thenReturn(List.of());

        List<IntegrationJob> result = claimService.claimPendingBatch(10);

        assertThat(result).isEmpty();
    }

    @Test
    void claimPendingBatch_handlesMultipleJobs() {
        IntegrationJob job1 = pendingJob();
        IntegrationJob job2 = pendingJob();
        job2.setAttemptCount(2);
        when(jobRepository.claimPendingBatch(any(Instant.class), eq(10)))
                .thenReturn(List.of(job1, job2));

        List<IntegrationJob> result = claimService.claimPendingBatch(10);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAttemptCount()).isEqualTo(1);
        assertThat(result.get(1).getAttemptCount()).isEqualTo(3);
        result.forEach(j -> assertThat(j.getStatus()).isEqualTo(IntegrationJobStatus.PROCESSING));
    }

    // ── claimRetryBatch ───────────────────────────────────────────────────────

    @Test
    void claimRetryBatch_queriesRepositoryWithCorrectLimit() {
        when(jobRepository.claimRetryBatch(any(Instant.class), eq(5))).thenReturn(List.of());

        claimService.claimRetryBatch(5);

        verify(jobRepository).claimRetryBatch(any(Instant.class), eq(5));
    }

    @Test
    void claimRetryBatch_setsStatusToProcessingAndIncrementsAttemptCount() {
        IntegrationJob job = failedJob(2);
        when(jobRepository.claimRetryBatch(any(Instant.class), eq(10)))
                .thenReturn(List.of(job));

        List<IntegrationJob> result = claimService.claimRetryBatch(10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(IntegrationJobStatus.PROCESSING);
        assertThat(result.get(0).getAttemptCount()).isEqualTo(3); // was 2
    }

    @Test
    void claimRetryBatch_returnsEmptyList_whenNoEligibleJobs() {
        when(jobRepository.claimRetryBatch(any(Instant.class), eq(10)))
                .thenReturn(List.of());

        List<IntegrationJob> result = claimService.claimRetryBatch(10);

        assertThat(result).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private IntegrationJob pendingJob() {
        IntegrationJob job = new IntegrationJob();
        job.setIntegrationType(IntegrationType.SLACK_NOTIFICATION);
        job.setStatus(IntegrationJobStatus.PENDING);
        job.setAttemptCount(0);
        job.setMaxAttempts(3);
        return job;
    }

    private IntegrationJob failedJob(int attemptCount) {
        IntegrationJob job = new IntegrationJob();
        job.setIntegrationType(IntegrationType.SLACK_NOTIFICATION);
        job.setStatus(IntegrationJobStatus.FAILED);
        job.setAttemptCount(attemptCount);
        job.setMaxAttempts(5);
        return job;
    }
}
