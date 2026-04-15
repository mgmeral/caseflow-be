package com.caseflow.ticket.service;

import com.caseflow.common.exception.InvalidTicketStateException;
import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.ticket.api.dto.BulkActionResult;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.workflow.assignment.AssignmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkTicketActionServiceTest {

    @Mock private TicketService ticketService;
    @Mock private AssignmentService assignmentService;
    @Mock private TagService tagService;

    @InjectMocks
    private BulkTicketActionService sut;

    // ── bulkAssign ────────────────────────────────────────────────────────────

    @Test
    void bulkAssign_succeedsForAllTickets() {
        List<Long> ids = List.of(1L, 2L, 3L);

        BulkActionResult result = sut.bulkAssign(ids, 10L, 20L, 99L);

        assertThat(result.succeededCount()).isEqualTo(3);
        assertThat(result.failedCount()).isZero();
        assertThat(result.succeededIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
        verify(assignmentService, times(3)).reassign(any(), eq(10L), eq(20L), eq(99L));
    }

    @Test
    void bulkAssign_partialSuccess_whenSomeTicketsFail() {
        lenient().doThrow(new TicketNotFoundException(2L)).when(assignmentService).reassign(eq(2L), any(), any(), any());

        BulkActionResult result = sut.bulkAssign(List.of(1L, 2L, 3L), 10L, null, 99L);

        assertThat(result.succeededCount()).isEqualTo(2);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.succeededIds()).containsExactlyInAnyOrder(1L, 3L);
        assertThat(result.failures()).containsKey(2L);
    }

    @Test
    void bulkAssign_failsAll_whenBothAssigneeAndGroupNull() {
        BulkActionResult result = sut.bulkAssign(List.of(1L, 2L), null, null, 99L);

        assertThat(result.succeededCount()).isZero();
        assertThat(result.failedCount()).isEqualTo(2);
    }

    // ── bulkAddTags ───────────────────────────────────────────────────────────

    @Test
    void bulkAddTags_succeedsForAllTickets() {
        List<Long> ticketIds = List.of(1L, 2L);
        List<Long> tagIds = List.of(100L, 200L);

        BulkActionResult result = sut.bulkAddTags(ticketIds, tagIds, 99L);

        assertThat(result.succeededCount()).isEqualTo(2);
        assertThat(result.failedCount()).isZero();
        // 2 tickets × 2 tags = 4 addTagToTicket calls
        verify(tagService, times(4)).addTagToTicket(any(), any(), eq(99L));
    }

    @Test
    void bulkAddTags_partialFailure_whenTicketNotFound() {
        lenient().doThrow(new TicketNotFoundException(99L)).when(tagService).addTagToTicket(eq(99L), any(), any());

        BulkActionResult result = sut.bulkAddTags(List.of(1L, 99L), List.of(10L), 5L);

        assertThat(result.succeededCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.failures()).containsKey(99L);
    }

    // ── bulkStatusChange ──────────────────────────────────────────────────────

    @Test
    void bulkStatusChange_succeedsForAllTickets() {
        List<Long> ids = List.of(1L, 2L, 3L);

        BulkActionResult result = sut.bulkStatusChange(ids, TicketStatus.RESOLVED, 99L);

        assertThat(result.succeededCount()).isEqualTo(3);
        assertThat(result.failedCount()).isZero();
        verify(ticketService, times(3)).changeStatus(any(), eq(TicketStatus.RESOLVED), eq(99L));
    }

    @Test
    void bulkStatusChange_partialFailure_whenTransitionInvalid() {
        lenient().doThrow(new InvalidTicketStateException(TicketStatus.CLOSED, TicketStatus.RESOLVED))
                .when(ticketService).changeStatus(eq(2L), eq(TicketStatus.RESOLVED), any());

        BulkActionResult result = sut.bulkStatusChange(List.of(1L, 2L, 3L), TicketStatus.RESOLVED, 99L);

        assertThat(result.succeededCount()).isEqualTo(2);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.failures()).containsKey(2L);
    }

    @Test
    void bulkStatusChange_emptyBatch_returnsEmptySuccess() {
        BulkActionResult result = sut.bulkStatusChange(List.of(), TicketStatus.CLOSED, 99L);

        assertThat(result.succeededCount()).isZero();
        assertThat(result.failedCount()).isZero();
    }
}
