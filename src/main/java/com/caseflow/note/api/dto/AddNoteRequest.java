package com.caseflow.note.api.dto;

import com.caseflow.note.domain.NoteType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AddNoteRequest(

        @NotNull
        Long ticketId,

        @NotBlank
        String content,

        @NotNull
        NoteType type,

        /**
         * IDs of users to @mention in this note.
         * Only processed for {@link NoteType#INTERNAL} notes; ignored otherwise.
         * Duplicate ids are deduplicated. Max 20 mentions per note.
         * Self-mentions are accepted in input but do not produce a notification.
         */
        @Size(max = 20)
        List<Long> mentionedUserIds
) {}
