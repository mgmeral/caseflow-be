package com.caseflow.ai.service;

import com.caseflow.ai.domain.AiSyncStatus;
import com.caseflow.ai.domain.TicketAiIndex;
import com.caseflow.ai.repository.TicketAiIndexRepository;
import com.caseflow.ai.repository.TicketAiResponseCacheRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSourceVersionServiceTest {

    @Mock private TicketAiIndexRepository indexRepository;
    @Mock private TicketAiResponseCacheRepository cacheRepository;

    @InjectMocks
    private AiSourceVersionService sut;

    @Test
    void onAiRelevantChange_createsNewIndex_whenNoneExists() {
        when(indexRepository.incrementSourceVersion(1L)).thenReturn(0);
        when(cacheRepository.invalidateByTicketId(1L)).thenReturn(0);

        sut.onAiRelevantChange(1L);

        ArgumentCaptor<TicketAiIndex> captor = ArgumentCaptor.forClass(TicketAiIndex.class);
        verify(indexRepository).save(captor.capture());
        TicketAiIndex saved = captor.getValue();
        assertThat(saved.getTicketId()).isEqualTo(1L);
        assertThat(saved.getSourceVersion()).isEqualTo(1L);
        assertThat(saved.getIndexedVersion()).isEqualTo(0L);
        assertThat(saved.getSyncStatus()).isEqualTo(AiSyncStatus.STALE);
    }

    @Test
    void onAiRelevantChange_incrementsVersion_whenExists() {
        when(indexRepository.incrementSourceVersion(1L)).thenReturn(1);
        when(cacheRepository.invalidateByTicketId(1L)).thenReturn(2);

        sut.onAiRelevantChange(1L);

        verify(indexRepository, never()).save(any());
        verify(cacheRepository).invalidateByTicketId(1L);
    }

    @Test
    void onAiRelevantChange_invalidatesCacheEntries() {
        when(indexRepository.incrementSourceVersion(1L)).thenReturn(1);
        when(cacheRepository.invalidateByTicketId(1L)).thenReturn(3);

        sut.onAiRelevantChange(1L);

        verify(cacheRepository).invalidateByTicketId(1L);
    }

    @Test
    void ensureIndexExists_createsRow_whenMissing() {
        when(indexRepository.findByTicketId(42L)).thenReturn(Optional.empty());

        sut.ensureIndexExists(42L);

        ArgumentCaptor<TicketAiIndex> captor = ArgumentCaptor.forClass(TicketAiIndex.class);
        verify(indexRepository).save(captor.capture());
        assertThat(captor.getValue().getTicketId()).isEqualTo(42L);
        assertThat(captor.getValue().getSyncStatus()).isEqualTo(AiSyncStatus.PENDING);
    }

    @Test
    void ensureIndexExists_doesNotCreate_whenAlreadyExists() {
        TicketAiIndex existing = new TicketAiIndex();
        when(indexRepository.findByTicketId(42L)).thenReturn(Optional.of(existing));

        sut.ensureIndexExists(42L);

        verify(indexRepository, never()).save(any());
    }

    @Test
    void currentSourceVersion_returnsZero_whenNoIndex() {
        when(indexRepository.findByTicketId(1L)).thenReturn(Optional.empty());

        assertThat(sut.currentSourceVersion(1L)).isEqualTo(0L);
    }

    @Test
    void currentSourceVersion_returnsVersion_whenIndexExists() {
        TicketAiIndex index = new TicketAiIndex();
        index.setSourceVersion(5L);
        when(indexRepository.findByTicketId(1L)).thenReturn(Optional.of(index));

        assertThat(sut.currentSourceVersion(1L)).isEqualTo(5L);
    }

    @Test
    void ticketAiIndex_isStale_whenSourceVersionAheadOfIndexed() {
        TicketAiIndex index = new TicketAiIndex();
        index.setSourceVersion(3L);
        index.setIndexedVersion(2L);

        assertThat(index.isStale()).isTrue();
    }

    @Test
    void ticketAiIndex_isNotStale_whenVersionsMatch() {
        TicketAiIndex index = new TicketAiIndex();
        index.setSourceVersion(3L);
        index.setIndexedVersion(3L);

        assertThat(index.isStale()).isFalse();
    }

    @Test
    void ticketAiIndex_markSynced_resetsFailureCount() {
        TicketAiIndex index = new TicketAiIndex();
        index.markFailed("TIMEOUT", "Connection timed out");
        index.markFailed("TIMEOUT", "Connection timed out");

        index.markSynced(3L, "text-embedding-ada-002");

        assertThat(index.getSyncStatus()).isEqualTo(AiSyncStatus.SYNCED);
        assertThat(index.getConsecutiveFailureCount()).isEqualTo(0);
        assertThat(index.getIndexedVersion()).isEqualTo(3L);
        assertThat(index.getLastError()).isNull();
    }

    @Test
    void ticketAiIndex_markFailed_incrementsFailureCount() {
        TicketAiIndex index = new TicketAiIndex();

        index.markFailed("HTTP_503", "Service unavailable");
        index.markFailed("HTTP_503", "Service unavailable");

        assertThat(index.getSyncStatus()).isEqualTo(AiSyncStatus.FAILED);
        assertThat(index.getConsecutiveFailureCount()).isEqualTo(2);
        assertThat(index.getLastErrorCode()).isEqualTo("HTTP_503");
    }
}
