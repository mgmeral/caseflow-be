package com.caseflow.email.api.mapper;

import com.caseflow.email.api.dto.DispatchResponse;
import com.caseflow.email.domain.OutboundEmailDispatch;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DispatchMapper {

    public DispatchResponse toResponse(OutboundEmailDispatch dispatch) {
        return new DispatchResponse(
                dispatch.getId(),
                dispatch.getTicketId(),
                dispatch.getMessageId(),
                dispatch.getFromAddress(),
                dispatch.getToAddress(),
                dispatch.getSubject(),
                dispatch.getStatus(),
                dispatch.getAttempts(),
                dispatch.getLastAttemptAt(),
                dispatch.getSentAt(),
                dispatch.getFailureReason(),
                dispatch.getScheduledAt(),
                dispatch.getCreatedAt()
        );
    }

    public List<DispatchResponse> toResponseList(List<OutboundEmailDispatch> dispatches) {
        return dispatches.stream().map(this::toResponse).toList();
    }
}
