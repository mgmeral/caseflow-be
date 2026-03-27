package com.caseflow.workflow.history;

import com.caseflow.ticket.domain.History;
import com.caseflow.ticket.repository.HistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TicketHistoryService {

    private final HistoryRepository historyRepository;

    public TicketHistoryService(HistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    @Transactional
    public void record(Long ticketId, String actionType, Long performedBy, String details) {
        History history = new History();
        history.setTicketId(ticketId);
        history.setActionType(actionType);
        history.setPerformedBy(performedBy);
        history.setDetails(details);
        historyRepository.save(history);
    }

    @Transactional
    public void recordCreated(Long ticketId, Long performedBy) {
        record(ticketId, "CREATED", performedBy, null);
    }

    @Transactional
    public void recordAssigned(Long ticketId, Long performedBy, Long userId, Long groupId) {
        record(ticketId, "ASSIGNED", performedBy, "userId=" + userId + ",groupId=" + groupId);
    }

    @Transactional
    public void recordReassigned(Long ticketId, Long performedBy, Long newUserId, Long newGroupId) {
        record(ticketId, "REASSIGNED", performedBy, "userId=" + newUserId + ",groupId=" + newGroupId);
    }

    @Transactional
    public void recordUnassigned(Long ticketId, Long performedBy) {
        record(ticketId, "UNASSIGNED", performedBy, null);
    }

    @Transactional
    public void recordTransferred(Long ticketId, Long performedBy, Long fromGroupId, Long toGroupId) {
        record(ticketId, "TRANSFERRED", performedBy, "from=" + fromGroupId + ",to=" + toGroupId);
    }

    @Transactional
    public void recordStatusChanged(Long ticketId, Long performedBy, String from, String to) {
        record(ticketId, "STATUS_CHANGED", performedBy, from + " -> " + to);
    }

    @Transactional
    public void recordNoteAdded(Long ticketId, Long performedBy) {
        record(ticketId, "NOTE_ADDED", performedBy, null);
    }

    @Transactional
    public void recordClosed(Long ticketId, Long performedBy) {
        record(ticketId, "CLOSED", performedBy, null);
    }

    @Transactional
    public void recordReopened(Long ticketId, Long performedBy) {
        record(ticketId, "REOPENED", performedBy, null);
    }

    @Transactional(readOnly = true)
    public List<History> getByTicket(Long ticketId) {
        return historyRepository.findByTicketIdOrderByPerformedAtAsc(ticketId);
    }
}
