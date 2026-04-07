package com.caseflow.workflow.history;

import com.caseflow.ticket.domain.History;
import com.caseflow.ticket.domain.TicketEventType;
import com.caseflow.ticket.repository.HistoryRepository;
import com.caseflow.ticket.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Records immutable ticket lifecycle events.
 *
 * <p>All {@code record*} methods are transactional writes. The {@code performedBy}
 * parameter is nullable for system-generated events; when null the event is
 * stored with {@code source_type = SYSTEM}.
 *
 * <p>Each event also carries the ticket's stable {@code publicId} so downstream
 * consumers can correlate events without a tickets-table join.
 */
@Service
public class TicketHistoryService {

    private final HistoryRepository historyRepository;
    private final TicketRepository ticketRepository;

    public TicketHistoryService(HistoryRepository historyRepository,
                                TicketRepository ticketRepository) {
        this.historyRepository = historyRepository;
        this.ticketRepository = ticketRepository;
    }

    // ── Generic write ─────────────────────────────────────────────────────────

    @Transactional
    public void record(Long ticketId, String actionType, Long performedBy, String details) {
        UUID publicId = resolvePublicId(ticketId);
        History h = build(ticketId, publicId, actionType, performedBy, details);
        historyRepository.save(h);
    }

    // ── Ticket lifecycle ──────────────────────────────────────────────────────

    @Transactional
    public void recordCreated(Long ticketId, Long performedBy) {
        UUID publicId = resolvePublicId(ticketId);
        History h = build(ticketId, publicId, TicketEventType.TICKET_CREATED, performedBy, null);
        h.setSummary("Ticket created");
        historyRepository.save(h);
    }

    @Transactional
    public void recordStatusChanged(Long ticketId, Long performedBy, String from, String to) {
        UUID publicId = resolvePublicId(ticketId);
        History h = build(ticketId, publicId, TicketEventType.STATUS_CHANGED, performedBy,
                from + " -> " + to);
        h.setSummary("Status changed: " + from + " \u2192 " + to);
        h.setOldValueJson("\"" + from + "\"");
        h.setNewValueJson("\"" + to + "\"");
        historyRepository.save(h);
    }

    @Transactional
    public void recordPriorityChanged(Long ticketId, Long performedBy, String from, String to) {
        UUID publicId = resolvePublicId(ticketId);
        History h = build(ticketId, publicId, TicketEventType.PRIORITY_CHANGED, performedBy,
                from + " -> " + to);
        h.setSummary("Priority changed: " + from + " \u2192 " + to);
        h.setOldValueJson("\"" + from + "\"");
        h.setNewValueJson("\"" + to + "\"");
        historyRepository.save(h);
    }

    @Transactional
    public void recordAssigned(Long ticketId, Long performedBy, Long userId, Long groupId) {
        UUID publicId = resolvePublicId(ticketId);
        History h = build(ticketId, publicId, TicketEventType.ASSIGNED_TO_USER, performedBy,
                "userId=" + userId + ",groupId=" + groupId);
        h.setSummary("Assigned to user " + userId);
        h.setMetadataJson("{\"userId\":" + userId + ",\"groupId\":" + groupId + "}");
        historyRepository.save(h);
    }

    @Transactional
    public void recordReassigned(Long ticketId, Long performedBy, Long newUserId, Long newGroupId) {
        UUID publicId = resolvePublicId(ticketId);
        History h = build(ticketId, publicId, TicketEventType.ASSIGNED_TO_USER, performedBy,
                "userId=" + newUserId + ",groupId=" + newGroupId);
        h.setSummary("Reassigned to user " + newUserId);
        h.setMetadataJson("{\"userId\":" + newUserId + ",\"groupId\":" + newGroupId + "}");
        historyRepository.save(h);
    }

    @Transactional
    public void recordUnassigned(Long ticketId, Long performedBy) {
        UUID publicId = resolvePublicId(ticketId);
        History h = build(ticketId, publicId, "UNASSIGNED", performedBy, null);
        h.setSummary("Assignment removed");
        historyRepository.save(h);
    }

    @Transactional
    public void recordTransferred(Long ticketId, Long performedBy, Long fromGroupId, Long toGroupId) {
        UUID publicId = resolvePublicId(ticketId);
        History h = build(ticketId, publicId, TicketEventType.TRANSFERRED, performedBy,
                "from=" + fromGroupId + ",to=" + toGroupId);
        h.setSummary("Transferred from group " + fromGroupId + " to group " + toGroupId);
        h.setMetadataJson("{\"fromGroupId\":" + fromGroupId + ",\"toGroupId\":" + toGroupId + "}");
        historyRepository.save(h);
    }

    @Transactional
    public void recordNoteAdded(Long ticketId, Long performedBy) {
        UUID publicId = resolvePublicId(ticketId);
        History h = build(ticketId, publicId, TicketEventType.INTERNAL_NOTE_ADDED, performedBy, null);
        h.setSummary("Internal note added");
        historyRepository.save(h);
    }

    @Transactional
    public void recordClosed(Long ticketId, Long performedBy) {
        UUID publicId = resolvePublicId(ticketId);
        History h = build(ticketId, publicId, "CLOSED", performedBy, null);
        h.setSummary("Ticket closed");
        historyRepository.save(h);
    }

    @Transactional
    public void recordReopened(Long ticketId, Long performedBy) {
        UUID publicId = resolvePublicId(ticketId);
        History h = build(ticketId, publicId, "REOPENED", performedBy, null);
        h.setSummary("Ticket reopened");
        historyRepository.save(h);
    }

    // ── Email activity ────────────────────────────────────────────────────────

    /**
     * Records that an inbound email was received and linked to this ticket.
     * This is a system event — no human actor.
     */
    @Transactional
    public void recordInboundEmailReceived(Long ticketId, UUID ticketPublicId,
                                           Long ingressEventId, String fromAddress) {
        History h = build(ticketId, ticketPublicId, TicketEventType.INBOUND_EMAIL_RECEIVED,
                null, null);
        h.setSourceType("SYSTEM");
        h.setSummary("Inbound email received from " + fromAddress);
        h.setMetadataJson("{\"ingressEventId\":" + ingressEventId
                + ",\"from\":\"" + escapeJson(fromAddress) + "\"}");
        historyRepository.save(h);
    }

    /**
     * Records that an outbound reply was queued for sending.
     * This is a user-initiated event.
     */
    @Transactional
    public void recordOutboundReplyQueued(Long ticketId, UUID ticketPublicId,
                                          Long dispatchId, String toAddress, Long sentByUserId) {
        History h = build(ticketId, ticketPublicId, TicketEventType.OUTBOUND_REPLY_QUEUED,
                sentByUserId, "to=" + toAddress + ";dispatchId=" + dispatchId);
        h.setSummary("Reply queued to " + toAddress);
        h.setMetadataJson("{\"dispatchId\":" + dispatchId
                + ",\"toAddress\":\"" + escapeJson(toAddress) + "\"}");
        historyRepository.save(h);
    }

    /**
     * Records that an outbound reply was successfully sent via SMTP.
     * This is a system event (scheduler).
     */
    @Transactional
    public void recordOutboundReplySent(Long ticketId, Long dispatchId) {
        UUID publicId = resolvePublicId(ticketId);
        History h = build(ticketId, publicId, TicketEventType.OUTBOUND_REPLY_SENT, null, null);
        h.setSourceType("SYSTEM");
        h.setSummary("Outbound reply sent");
        h.setMetadataJson("{\"dispatchId\":" + dispatchId + "}");
        historyRepository.save(h);
    }

    /**
     * Records that an outbound reply failed to send (possibly permanently).
     * This is a system event (scheduler).
     */
    @Transactional
    public void recordOutboundReplyFailed(Long ticketId, Long dispatchId, String reason,
                                          boolean permanent) {
        UUID publicId = resolvePublicId(ticketId);
        History h = build(ticketId, publicId, TicketEventType.OUTBOUND_REPLY_FAILED, null, null);
        h.setSourceType("SYSTEM");
        h.setSummary(permanent ? "Outbound reply permanently failed" : "Outbound reply send attempt failed");
        h.setMetadataJson("{\"dispatchId\":" + dispatchId
                + ",\"permanent\":" + permanent
                + ",\"reason\":\"" + escapeJson(reason) + "\"}");
        historyRepository.save(h);
    }

    // ── Content events ────────────────────────────────────────────────────────

    @Transactional
    public void recordAttachmentAdded(Long ticketId, Long attachmentId, String fileName,
                                      Long performedBy) {
        UUID publicId = resolvePublicId(ticketId);
        History h = build(ticketId, publicId, TicketEventType.ATTACHMENT_ADDED, performedBy, null);
        h.setSummary("Attachment added: " + fileName);
        h.setMetadataJson("{\"attachmentId\":" + attachmentId
                + ",\"fileName\":\"" + escapeJson(fileName) + "\"}");
        historyRepository.save(h);
    }

    @Transactional
    public void recordTemplateUsed(Long ticketId, Long templateId, String templateCode,
                                   Long performedBy) {
        UUID publicId = resolvePublicId(ticketId);
        History h = build(ticketId, publicId, TicketEventType.TEMPLATE_USED, performedBy, null);
        h.setSummary("Template used: " + templateCode);
        h.setMetadataJson("{\"templateId\":" + templateId
                + ",\"templateCode\":\"" + escapeJson(templateCode) + "\"}");
        historyRepository.save(h);
    }

    // ── Tag events ────────────────────────────────────────────────────────────

    @Transactional
    public void recordTagAdded(Long ticketId, UUID ticketPublicId,
                               Long tagId, String tagCode, Long performedBy) {
        History h = build(ticketId, ticketPublicId, TicketEventType.TAG_ADDED, performedBy, null);
        h.setSummary("Tag added: " + tagCode);
        h.setMetadataJson("{\"tagId\":" + tagId
                + ",\"tagCode\":\"" + escapeJson(tagCode) + "\"}");
        historyRepository.save(h);
    }

    @Transactional
    public void recordTagRemoved(Long ticketId, UUID ticketPublicId,
                                 Long tagId, String tagCode, Long performedBy) {
        History h = build(ticketId, ticketPublicId, TicketEventType.TAG_REMOVED, performedBy, null);
        h.setSummary("Tag removed: " + tagCode);
        h.setMetadataJson("{\"tagId\":" + tagId
                + ",\"tagCode\":\"" + escapeJson(tagCode) + "\"}");
        historyRepository.save(h);
    }

    // ── Jira integration events ───────────────────────────────────────────────

    @Transactional
    public void recordJiraCreateRequested(Long ticketId, UUID ticketPublicId,
                                          Long jobId, Long performedBy) {
        History h = build(ticketId, ticketPublicId, TicketEventType.JIRA_ISSUE_CREATE_REQUESTED,
                performedBy, null);
        h.setSummary("Jira issue creation requested");
        h.setMetadataJson("{\"jobId\":" + jobId + "}");
        historyRepository.save(h);
    }

    @Transactional
    public void recordJiraIssueCreated(Long ticketId, UUID ticketPublicId,
                                       Long jobId, String issueKey, String issueUrl) {
        History h = build(ticketId, ticketPublicId, TicketEventType.JIRA_ISSUE_CREATED,
                null, null);
        h.setSourceType("SYSTEM");
        h.setSummary("Jira issue created: " + issueKey);
        h.setMetadataJson("{\"jobId\":" + jobId
                + ",\"issueKey\":\"" + escapeJson(issueKey) + "\""
                + ",\"issueUrl\":\"" + escapeJson(issueUrl) + "\"}");
        historyRepository.save(h);
    }

    @Transactional
    public void recordJiraCreateFailed(Long ticketId, UUID ticketPublicId,
                                       Long jobId, String reason, boolean permanent) {
        History h = build(ticketId, ticketPublicId, TicketEventType.JIRA_ISSUE_CREATE_FAILED,
                null, null);
        h.setSourceType("SYSTEM");
        h.setSummary(permanent ? "Jira issue creation permanently failed"
                : "Jira issue creation attempt failed");
        h.setMetadataJson("{\"jobId\":" + jobId
                + ",\"permanent\":" + permanent
                + ",\"reason\":\"" + escapeJson(reason) + "\"}");
        historyRepository.save(h);
    }

    // ── External notification events ──────────────────────────────────────────

    @Transactional
    public void recordExternalNotificationSent(Long ticketId, UUID ticketPublicId,
                                               Long jobId, String channelType,
                                               String channelName, String eventType) {
        History h = build(ticketId, ticketPublicId, TicketEventType.EXTERNAL_NOTIFICATION_SENT,
                null, null);
        h.setSourceType("SYSTEM");
        h.setSummary("External notification sent: " + channelName + " [" + eventType + "]");
        h.setMetadataJson("{\"jobId\":" + jobId
                + ",\"channelType\":\"" + escapeJson(channelType) + "\""
                + ",\"channelName\":\"" + escapeJson(channelName) + "\""
                + ",\"eventType\":\"" + escapeJson(eventType) + "\"}");
        historyRepository.save(h);
    }

    @Transactional
    public void recordExternalNotificationFailed(Long ticketId, UUID ticketPublicId,
                                                 Long jobId, String channelType,
                                                 String channelName, String reason) {
        History h = build(ticketId, ticketPublicId, TicketEventType.EXTERNAL_NOTIFICATION_FAILED,
                null, null);
        h.setSourceType("SYSTEM");
        h.setSummary("External notification failed: " + channelName);
        h.setMetadataJson("{\"jobId\":" + jobId
                + ",\"channelType\":\"" + escapeJson(channelType) + "\""
                + ",\"channelName\":\"" + escapeJson(channelName) + "\""
                + ",\"reason\":\"" + escapeJson(reason) + "\"}");
        historyRepository.save(h);
    }

    // ── Scheduled email events ────────────────────────────────────────────────

    @Transactional
    public void recordScheduledEmailCreated(Long ticketId, UUID ticketPublicId,
                                            Long dispatchId, String toAddress,
                                            java.time.Instant sendNotBefore, Long performedBy) {
        History h = build(ticketId, ticketPublicId, TicketEventType.SCHEDULED_EMAIL_CREATED,
                performedBy, null);
        h.setSummary("Scheduled email created for " + toAddress);
        h.setMetadataJson("{\"dispatchId\":" + dispatchId
                + ",\"toAddress\":\"" + escapeJson(toAddress) + "\""
                + ",\"sendNotBefore\":\"" + sendNotBefore + "\"}");
        historyRepository.save(h);
    }

    @Transactional
    public void recordScheduledEmailCanceled(Long ticketId, UUID ticketPublicId,
                                              Long dispatchId, Long performedBy) {
        History h = build(ticketId, ticketPublicId, TicketEventType.SCHEDULED_EMAIL_CANCELED,
                performedBy, null);
        h.setSummary("Scheduled email canceled");
        h.setMetadataJson("{\"dispatchId\":" + dispatchId + "}");
        historyRepository.save(h);
    }

    @Transactional
    public void recordScheduledEmailSent(Long ticketId, UUID ticketPublicId, Long dispatchId) {
        History h = build(ticketId, ticketPublicId, TicketEventType.SCHEDULED_EMAIL_SENT, null, null);
        h.setSourceType("SYSTEM");
        h.setSummary("Scheduled email sent");
        h.setMetadataJson("{\"dispatchId\":" + dispatchId + "}");
        historyRepository.save(h);
    }

    @Transactional
    public void recordScheduledEmailFailed(Long ticketId, Long dispatchId, String reason) {
        UUID publicId = resolvePublicId(ticketId);
        History h = build(ticketId, publicId, TicketEventType.SCHEDULED_EMAIL_FAILED, null, null);
        h.setSourceType("SYSTEM");
        h.setSummary("Scheduled email failed: " + truncate(reason, 200));
        h.setMetadataJson("{\"dispatchId\":" + dispatchId
                + ",\"reason\":\"" + escapeJson(reason) + "\"}");
        historyRepository.save(h);
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<History> getByTicket(Long ticketId) {
        return historyRepository.findByTicketIdOrderByPerformedAtAsc(ticketId);
    }

    @Transactional(readOnly = true)
    public List<History> getByTicketPublicId(UUID ticketPublicId) {
        return historyRepository.findByTicketPublicIdOrderByPerformedAtAsc(ticketPublicId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private History build(Long ticketId, UUID ticketPublicId, String actionType,
                          Long performedBy, String details) {
        History h = new History();
        h.setTicketId(ticketId);
        h.setTicketPublicId(ticketPublicId);
        h.setActionType(actionType);
        h.setPerformedBy(performedBy);
        h.setDetails(details);
        h.setSourceType(performedBy != null ? "USER" : "SYSTEM");
        return h;
    }

    private UUID resolvePublicId(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .map(t -> t.getPublicId())
                .orElse(null);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
