package com.caseflow.email.api.mapper;

import com.caseflow.email.api.dto.IngressEventResponse;
import com.caseflow.email.domain.EmailIngressEvent;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IngressEventMapper {

    public IngressEventResponse toResponse(EmailIngressEvent event) {
        return new IngressEventResponse(
                event.getId(),
                event.getMailboxId(),
                event.getMessageId(),
                event.getRawFrom(),
                event.getRawSubject(),
                event.getReceivedAt(),
                event.getStatus(),
                event.getFailureReason(),
                event.getProcessingAttempts(),
                event.getLastAttemptAt(),
                event.getProcessedAt(),
                event.getDocumentId(),
                event.getTicketId()
        );
    }

    public List<IngressEventResponse> toResponseList(List<EmailIngressEvent> events) {
        return events.stream().map(this::toResponse).toList();
    }
}
