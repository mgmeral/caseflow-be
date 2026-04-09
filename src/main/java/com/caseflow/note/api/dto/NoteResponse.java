package com.caseflow.note.api.dto;

import com.caseflow.note.domain.NoteType;

import java.time.Instant;
import java.util.List;

public record NoteResponse(
        Long id,
        Long ticketId,
        String content,
        NoteType type,
        /** Numeric author id — kept for backward compatibility. */
        Long createdBy,
        /** Author summary — replaces the need for a separate user lookup. */
        UserSummary createdByUser,
        /** Structured mentions — empty list when no users are mentioned. */
        List<UserSummary> mentions,
        Instant createdAt
) {}
