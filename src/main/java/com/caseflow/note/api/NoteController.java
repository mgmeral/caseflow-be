package com.caseflow.note.api;

import com.caseflow.common.security.SecurityContextHelper;
import com.caseflow.note.api.dto.AddNoteRequest;
import com.caseflow.note.api.dto.NoteResponse;
import com.caseflow.note.api.mapper.NoteMapper;
import com.caseflow.note.service.NoteService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Notes", description = "Internal ticket notes")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteService noteService;
    private final NoteMapper noteMapper;

    public NoteController(NoteService noteService, NoteMapper noteMapper) {
        this.noteService = noteService;
        this.noteMapper = noteMapper;
    }

    @PostMapping
    public ResponseEntity<NoteResponse> addNote(@Valid @RequestBody AddNoteRequest request) {
        Long userId = SecurityContextHelper.requireCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(
                noteMapper.toResponse(noteService.addNote(
                        request.ticketId(), request.content(), request.type(), userId))
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<NoteResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(noteMapper.toResponse(noteService.getById(id)));
    }

    @GetMapping("/by-ticket/{ticketId}")
    public ResponseEntity<List<NoteResponse>> getByTicket(@PathVariable Long ticketId) {
        return ResponseEntity.ok(noteMapper.toResponseList(noteService.listByTicket(ticketId)));
    }
}
