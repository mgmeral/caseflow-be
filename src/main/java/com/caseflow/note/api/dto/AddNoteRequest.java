package com.caseflow.note.api.dto;

import com.caseflow.note.domain.NoteType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddNoteRequest(

        @NotNull
        Long ticketId,

        @NotBlank
        String content,

        @NotNull
        NoteType type
) {}
