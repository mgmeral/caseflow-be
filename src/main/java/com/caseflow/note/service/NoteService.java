package com.caseflow.note.service;

import com.caseflow.common.exception.NoteNotFoundException;
import com.caseflow.note.domain.Note;
import com.caseflow.note.domain.NoteType;
import com.caseflow.note.repository.NoteRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NoteService {

    private final NoteRepository noteRepository;
    private final TicketHistoryService ticketHistoryService;

    public NoteService(NoteRepository noteRepository, TicketHistoryService ticketHistoryService) {
        this.noteRepository = noteRepository;
        this.ticketHistoryService = ticketHistoryService;
    }

    @Transactional
    public Note addNote(Long ticketId, String content, NoteType type, Long createdBy) {
        Note note = new Note();
        note.setTicketId(ticketId);
        note.setContent(content);
        note.setType(type);
        note.setCreatedBy(createdBy);
        Note saved = noteRepository.save(note);
        ticketHistoryService.recordNoteAdded(ticketId, createdBy);
        return saved;
    }

    @Transactional(readOnly = true)
    public Note getById(Long noteId) {
        return noteRepository.findById(noteId)
                .orElseThrow(() -> new NoteNotFoundException(noteId));
    }

    @Transactional(readOnly = true)
    public List<Note> listByTicket(Long ticketId) {
        return noteRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }
}
