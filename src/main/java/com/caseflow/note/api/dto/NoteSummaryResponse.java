package com.caseflow.note.api.dto;

import com.caseflow.note.domain.NoteType;

import java.time.Instant;

public record NoteSummaryResponse(
        Long id,
        Long ticketId,
        NoteType type,
        Long createdBy,
        Instant createdAt
) {}
