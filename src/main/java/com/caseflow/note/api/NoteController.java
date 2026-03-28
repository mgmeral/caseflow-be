package com.caseflow.note.api;

import com.caseflow.common.security.SecurityContextHelper;
import com.caseflow.note.api.dto.AddNoteRequest;
import com.caseflow.note.api.dto.NoteResponse;
import com.caseflow.note.api.mapper.NoteMapper;
import com.caseflow.note.service.NoteService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(NoteController.class);

    private final NoteService noteService;
    private final NoteMapper noteMapper;

    public NoteController(NoteService noteService, NoteMapper noteMapper) {
        this.noteService = noteService;
        this.noteMapper = noteMapper;
    }

    @PostMapping
    public ResponseEntity<NoteResponse> addNote(@Valid @RequestBody AddNoteRequest request) {
        Long userId = SecurityContextHelper.requireCurrentUserId();
        log.info("POST /notes — ticketId: {}, type: {}, userId: {}", request.ticketId(), request.type(), userId);
        NoteResponse response = noteMapper.toResponse(noteService.addNote(
                request.ticketId(), request.content(), request.type(), userId));
        log.info("POST /notes succeeded — noteId: {}, ticketId: {}", response.id(), request.ticketId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<NoteResponse> getById(@PathVariable Long id) {
        log.info("GET /notes/{}", id);
        return ResponseEntity.ok(noteMapper.toResponse(noteService.getById(id)));
    }

    @GetMapping("/by-ticket/{ticketId}")
    public ResponseEntity<List<NoteResponse>> getByTicket(@PathVariable Long ticketId) {
        log.info("GET /notes/by-ticket/{}", ticketId);
        return ResponseEntity.ok(noteMapper.toResponseList(noteService.listByTicket(ticketId)));
    }
}
