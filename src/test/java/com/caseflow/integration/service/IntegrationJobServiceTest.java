package com.caseflow.integration.service;

import com.caseflow.integration.domain.IntegrationJob;
import com.caseflow.integration.domain.IntegrationJobStatus;
import com.caseflow.integration.domain.IntegrationType;
import com.caseflow.integration.repository.IntegrationJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationJobServiceTest {

    @Mock
    private IntegrationJobRepository jobRepository;

    @InjectMocks
    private IntegrationJobService service;

    private IntegrationJob existingJob;

    @BeforeEach
    void setUp() {
        existingJob = new IntegrationJob();
        existingJob.setIntegrationType(IntegrationType.JIRA_ISSUE_CREATE);
        existingJob.setStatus(IntegrationJobStatus.PENDING);
        existingJob.setAttemptCount(0);
        existingJob.setMaxAttempts(3);
        existingJob.setIdempotencyKey("JIRA_CREATE:1");
    }

    // ── enqueue ───────────────────────────────────────────────────────────────

    @Test
    void enqueue_createsNewJob_whenIdempotencyKeyIsNew() {
        when(jobRepository.findByIdempotencyKey("JIRA_CREATE:1")).thenReturn(Optional.empty());
        when(jobRepository.save(any(IntegrationJob.class))).thenAnswer(inv -> inv.getArgument(0));

        IntegrationJob result = service.enqueue(
                IntegrationType.JIRA_ISSUE_CREATE, 1L, UUID.randomUUID(),
                null, null, null, "JIRA_CREATE:1", "USER", 42L);

        assertThat(result.getIntegrationType()).isEqualTo(IntegrationType.JIRA_ISSUE_CREATE);
        assertThat(result.getStatus()).isEqualTo(IntegrationJobStatus.PENDING);
        verify(jobRepository).save(any(IntegrationJob.class));
    }

    @Test
    void enqueue_returnsExistingJob_whenIdempotencyKeyExists() {
        when(jobRepository.findByIdempotencyKey("JIRA_CREATE:1"))
                .thenReturn(Optional.of(existingJob));

        IntegrationJob result = service.enqueue(
                IntegrationType.JIRA_ISSUE_CREATE, 1L, UUID.randomUUID(),
                null, null, null, "JIRA_CREATE:1", "USER", 42L);

        assertThat(result).isSameAs(existingJob);
        verify(jobRepository, never()).save(any(IntegrationJob.class));
    }

    // ── markSucceeded ─────────────────────────────────────────────────────────

    @Test
    void markSucceeded_setsStatusAndExternalRef() {
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markSucceeded(existingJob, "PROJ-123");

        assertThat(existingJob.getStatus()).isEqualTo(IntegrationJobStatus.SUCCEEDED);
        assertThat(existingJob.getExternalReference()).isEqualTo("PROJ-123");
        assertThat(existingJob.getProcessedAt()).isNotNull();
    }

    // ── markFailed ────────────────────────────────────────────────────────────

    @Test
    void markFailed_setsFAILED_whenAttemptsWithinBudget() {
        existingJob.setAttemptCount(1);
        existingJob.setMaxAttempts(3);
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markFailed(existingJob, "timeout");

        assertThat(existingJob.getStatus()).isEqualTo(IntegrationJobStatus.FAILED);
        assertThat(existingJob.getLastError()).contains("timeout");
        assertThat(existingJob.getNextAttemptAt()).isAfter(Instant.now().minusSeconds(5));
    }

    @Test
    void markFailed_setsPERMANENTLY_FAILED_whenMaxAttemptsReached() {
        existingJob.setAttemptCount(3);
        existingJob.setMaxAttempts(3);
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markFailed(existingJob, "final failure");

        assertThat(existingJob.getStatus()).isEqualTo(IntegrationJobStatus.PERMANENTLY_FAILED);
    }

    // ── resetForRetry ─────────────────────────────────────────────────────────

    @Test
    void resetForRetry_setsPENDING_fromFAILED() {
        existingJob.setStatus(IntegrationJobStatus.FAILED);
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.resetForRetry(existingJob);

        assertThat(existingJob.getStatus()).isEqualTo(IntegrationJobStatus.PENDING);
        assertThat(existingJob.getLastError()).isNull();
    }

    @Test
    void resetForRetry_throwsForSUCCEEDED() {
        existingJob.setStatus(IntegrationJobStatus.SUCCEEDED);

        assertThatThrownBy(() -> service.resetForRetry(existingJob))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── cancel ────────────────────────────────────────────────────────────────

    @Test
    void cancel_setsCANCELED_fromPENDING() {
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.cancel(existingJob);

        assertThat(existingJob.getStatus()).isEqualTo(IntegrationJobStatus.CANCELED);
        assertThat(existingJob.getCanceledAt()).isNotNull();
    }

    @Test
    void cancel_isNoOp_forAlreadyCanceled() {
        existingJob.setStatus(IntegrationJobStatus.CANCELED);

        service.cancel(existingJob);

        verify(jobRepository, never()).save(any());
    }

    @Test
    void cancel_isNoOp_forSUCCEEDED() {
        existingJob.setStatus(IntegrationJobStatus.SUCCEEDED);

        service.cancel(existingJob);

        verify(jobRepository, never()).save(any());
    }
}
