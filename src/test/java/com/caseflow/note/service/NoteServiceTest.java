package com.caseflow.note.service;

import com.caseflow.common.exception.NoteNotFoundException;
import com.caseflow.note.domain.Note;
import com.caseflow.note.domain.NoteType;
import com.caseflow.note.repository.NoteRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock
    private NoteRepository noteRepository;

    @Mock
    private TicketHistoryService ticketHistoryService;

    @InjectMocks
    private NoteService noteService;

    @Test
    void addNote_savesNoteAndRecordsHistory() {
        Note saved = buildNote(1L, 10L, NoteType.INFO, "Test content");
        when(noteRepository.save(any(Note.class))).thenReturn(saved);

        Note result = noteService.addNote(10L, "Test content", NoteType.INFO, 42L);

        assertThat(result).isNotNull();
        assertThat(result.getTicketId()).isEqualTo(10L);
        verify(ticketHistoryService).recordNoteAdded(eq(10L), eq(42L));
    }

    @Test
    void getById_returnsNote_whenFound() {
        Note note = buildNote(1L, 10L, NoteType.INVESTIGATION, "Investigation note");
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        Note result = noteService.getById(1L);
        assertThat(result.getType()).isEqualTo(NoteType.INVESTIGATION);
    }

    @Test
    void getById_throwsNoteNotFoundException_whenNotFound() {
        when(noteRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.getById(99L))
                .isInstanceOf(NoteNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void listByTicket_returnsOrderedNotes() {
        Note n1 = buildNote(1L, 10L, NoteType.INFO, "First");
        Note n2 = buildNote(2L, 10L, NoteType.ESCALATION, "Second");
        when(noteRepository.findByTicketIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(n1, n2));

        List<Note> results = noteService.listByTicket(10L);
        assertThat(results).hasSize(2);
    }

    private Note buildNote(Long id, Long ticketId, NoteType type, String content) {
        Note note = new Note();
        note.setTicketId(ticketId);
        note.setType(type);
        note.setContent(content);
        note.setCreatedBy(1L);
        try {
            var field = Note.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(note, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return note;
    }
}
