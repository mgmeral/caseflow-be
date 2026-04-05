package com.caseflow.email.api.mapper;

import com.caseflow.email.api.dto.IngressEventResponse;
import com.caseflow.email.domain.EmailIngressEvent;
import com.caseflow.ticket.api.dto.AttachmentMetadataResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IngressEventMapper {

    /**
     * Maps an event without attachment metadata (used in list/thread endpoints).
     * Attachments list is always empty — call {@link #toResponseWithAttachments} for the detail view.
     */
    public IngressEventResponse toResponse(EmailIngressEvent event) {
        return toResponseWithAttachments(event, List.of());
    }

    /**
     * Maps an event with pre-fetched Postgres attachment metadata.
     * Used in the detail endpoint (GET /inbound/{eventId}) where the caller loads
     * attachments by emailId and passes them here.
     */
    public IngressEventResponse toResponseWithAttachments(EmailIngressEvent event,
                                                          List<AttachmentMetadataResponse> attachments) {
        return new IngressEventResponse(
                event.getId(),
                event.getMailboxId(),
                event.getMessageId(),
                event.getRawFrom(),
                event.getRawSubject(),
                event.getInReplyTo(),
                event.getRawReplyTo(),
                event.getReceivedAt(),
                event.getStatus(),
                event.getFailureReason(),
                event.getProcessingAttempts(),
                event.getLastAttemptAt(),
                event.getProcessedAt(),
                event.getDocumentId(),
                event.getTicketId(),
                attachments != null ? attachments : List.of()
        );
    }

    public List<IngressEventResponse> toResponseList(List<EmailIngressEvent> events) {
        return events.stream().map(this::toResponse).toList();
    }
}
