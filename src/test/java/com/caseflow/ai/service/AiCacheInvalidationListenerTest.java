package com.caseflow.ai.service;

import com.caseflow.ticket.domain.TicketEventType;
import com.caseflow.workflow.history.TicketHistoryRecordedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AiCacheInvalidationListenerTest {

    private final AiSourceVersionService sourceVersionService = mock(AiSourceVersionService.class);
    private final AiCacheInvalidationListener listener = new AiCacheInvalidationListener(sourceVersionService);

    @ParameterizedTest
    @ValueSource(strings = {
            TicketEventType.INBOUND_EMAIL_RECEIVED,
            TicketEventType.OUTBOUND_REPLY_SENT,
            TicketEventType.SCHEDULED_EMAIL_SENT,
            TicketEventType.INTERNAL_NOTE_ADDED,
            TicketEventType.TAG_ADDED,
            TicketEventType.TAG_REMOVED,
            TicketEventType.STATUS_CHANGED,
            TicketEventType.PRIORITY_CHANGED,
            "CLOSED",
            "REOPENED"
    })
    void aiRelevantChange_invalidatesCache(String actionType) {
        listener.onTicketHistoryRecorded(new TicketHistoryRecordedEvent(5L, actionType));

        verify(sourceVersionService).onAiRelevantChange(5L);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            TicketEventType.TICKET_CREATED,
            TicketEventType.ASSIGNED_TO_USER,
            TicketEventType.TRANSFERRED,
            "UNASSIGNED",
            TicketEventType.JIRA_ISSUE_CREATED,
            TicketEventType.EXTERNAL_NOTIFICATION_SENT,
            TicketEventType.OUTBOUND_REPLY_QUEUED
    })
    void irrelevantChange_leavesCacheAlone(String actionType) {
        listener.onTicketHistoryRecorded(new TicketHistoryRecordedEvent(5L, actionType));

        verify(sourceVersionService, never()).onAiRelevantChange(anyLong());
    }

    @Test
    void invalidationFailure_isSwallowed() {
        doThrow(new RuntimeException("db down")).when(sourceVersionService).onAiRelevantChange(5L);

        assertThatCode(() -> listener.onTicketHistoryRecorded(
                new TicketHistoryRecordedEvent(5L, TicketEventType.INTERNAL_NOTE_ADDED)))
                .doesNotThrowAnyException();
    }
}
