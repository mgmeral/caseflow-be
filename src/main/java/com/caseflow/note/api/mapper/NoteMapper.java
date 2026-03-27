package com.caseflow.note.api.mapper;

import com.caseflow.note.api.dto.AddNoteRequest;
import com.caseflow.note.api.dto.NoteResponse;
import com.caseflow.note.api.dto.NoteSummaryResponse;
import com.caseflow.note.domain.Note;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface NoteMapper {

    // ── Entity → Response ─────────────────────────────────────────────────────

    NoteResponse toResponse(Note note);

    NoteSummaryResponse toSummaryResponse(Note note);

    List<NoteResponse> toResponseList(List<Note> notes);

    // ── Request → Entity ──────────────────────────────────────────────────────

    /**
     * ticketId, content, type map directly.
     * createdBy is resolved from SecurityContext in the controller — ignored here.
     * createdAt has no setter and is managed by @PrePersist — MapStruct skips it.
     */
    @org.mapstruct.Mapping(target = "createdBy", ignore = true)
    Note toEntity(AddNoteRequest request);
}
