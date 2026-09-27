package com.caseflow.knowledge.api.dto;

import com.caseflow.knowledge.domain.KnowledgeDocument;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeDocumentResponse(
        Long id,
        UUID publicId,
        String title,
        String body,
        String category,
        Long customerId,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static KnowledgeDocumentResponse from(KnowledgeDocument d) {
        return new KnowledgeDocumentResponse(d.getId(), d.getPublicId(), d.getTitle(), d.getBody(),
                d.getCategory(), d.getCustomerId(), Boolean.TRUE.equals(d.getIsActive()),
                d.getCreatedAt(), d.getUpdatedAt());
    }
}
