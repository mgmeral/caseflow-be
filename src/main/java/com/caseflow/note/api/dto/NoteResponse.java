package com.caseflow.note.api.dto;

import com.caseflow.note.domain.NoteType;

import java.time.Instant;

public record NoteResponse(
        Long id,
        Long ticketId,
        String content,
        NoteType type,
        Long createdBy,
        Instant createdAt
) {}
