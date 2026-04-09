package com.caseflow.note.service;

import com.caseflow.common.exception.NoteNotFoundException;
import com.caseflow.identity.domain.User;
import com.caseflow.identity.repository.UserRepository;
import com.caseflow.note.domain.Note;
import com.caseflow.note.domain.NoteMention;
import com.caseflow.note.domain.NoteType;
import com.caseflow.note.repository.NoteMentionRepository;
import com.caseflow.note.repository.NoteRepository;
import com.caseflow.notification.service.NotificationService;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock
    private NoteRepository noteRepository;

    @Mock
    private TicketHistoryService ticketHistoryService;

    @Mock
    private NoteMentionRepository noteMentionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NoteService noteService;

    // ── addNote — basic ───────────────────────────────────────────────────────

    @Test
    void addNote_savesNoteAndRecordsHistory() {
        Note saved = buildNote(1L, 10L, NoteType.INFO, "Test content");
        when(noteRepository.save(any(Note.class))).thenReturn(saved);

        Note result = noteService.addNote(10L, "Test content", NoteType.INFO, 42L, null);

        assertThat(result).isNotNull();
        assertThat(result.getTicketId()).isEqualTo(10L);
        verify(ticketHistoryService).recordNoteAdded(eq(10L), eq(42L));
    }

    // ── mentions — INTERNAL notes ─────────────────────────────────────────────

    @Test
    void addNote_persistsMentions_forInternalNote() {
        Note saved = buildNote(1L, 10L, NoteType.INTERNAL, "Content @alice");
        when(noteRepository.save(any(Note.class))).thenReturn(saved);

        User alice = buildUser(5L, "alice", "Alice Smith", true);
        when(userRepository.findAllById(List.of(5L))).thenReturn(List.of(alice));

        Ticket ticket = buildTicket(10L, "TKT-000010");
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(42L)).thenReturn(Optional.of(buildUser(42L, "creator", "Creator User", true)));

        noteService.addNote(10L, "Content @alice", NoteType.INTERNAL, 42L, List.of(5L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NoteMention>> captor = ArgumentCaptor.forClass(List.class);
        verify(noteMentionRepository).saveAll(captor.capture());
        List<NoteMention> persisted = captor.getValue();
        assertThat(persisted).hasSize(1);
        assertThat(persisted.get(0).getUserId()).isEqualTo(5L);
        assertThat(persisted.get(0).getNoteId()).isEqualTo(1L);
    }

    @Test
    void addNote_doesNotPersistMentions_forNonInternalNote() {
        Note saved = buildNote(1L, 10L, NoteType.INFO, "Content");
        when(noteRepository.save(any(Note.class))).thenReturn(saved);

        noteService.addNote(10L, "Content", NoteType.INFO, 42L, List.of(5L));

        verify(noteMentionRepository, never()).saveAll(any());
        verify(notificationService, never()).notifyUsersMentioned(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void addNote_deduplicatesMentions() {
        Note saved = buildNote(1L, 10L, NoteType.INTERNAL, "Content");
        when(noteRepository.save(any(Note.class))).thenReturn(saved);

        User alice = buildUser(5L, "alice", "Alice Smith", true);
        when(userRepository.findAllById(List.of(5L))).thenReturn(List.of(alice));

        Ticket ticket = buildTicket(10L, "TKT-000010");
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(42L)).thenReturn(Optional.of(buildUser(42L, "creator", "Creator", true)));

        // Pass duplicates: [5, 5, 5]
        noteService.addNote(10L, "Content", NoteType.INTERNAL, 42L, List.of(5L, 5L, 5L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NoteMention>> captor = ArgumentCaptor.forClass(List.class);
        verify(noteMentionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1); // deduplicated to 1
    }

    @Test
    void addNote_skipsInactiveUsers() {
        Note saved = buildNote(1L, 10L, NoteType.INTERNAL, "Content");
        when(noteRepository.save(any(Note.class))).thenReturn(saved);

        User inactive = buildUser(7L, "inactive", "Inactive User", false);
        when(userRepository.findAllById(List.of(7L))).thenReturn(List.of(inactive));

        noteService.addNote(10L, "Content", NoteType.INTERNAL, 42L, List.of(7L));

        // Inactive user — no mentions persisted, no notifications
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NoteMention>> captor = ArgumentCaptor.forClass(List.class);
        verify(noteMentionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
        verify(notificationService, never()).notifyUsersMentioned(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void addNote_selfMentionIsPersisted_butNoNotification() {
        long authorId = 42L;
        Note saved = buildNote(1L, 10L, NoteType.INTERNAL, "Content");
        when(noteRepository.save(any(Note.class))).thenReturn(saved);

        User author = buildUser(authorId, "author", "Author User", true);
        when(userRepository.findAllById(List.of(authorId))).thenReturn(List.of(author));

        Ticket ticket = buildTicket(10L, "TKT-000010");
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));

        noteService.addNote(10L, "Content", NoteType.INTERNAL, authorId, List.of(authorId));

        // Mention should be persisted
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NoteMention>> mentionCaptor = ArgumentCaptor.forClass(List.class);
        verify(noteMentionRepository).saveAll(mentionCaptor.capture());
        assertThat(mentionCaptor.getValue()).hasSize(1);

        // NotificationService receives the self-mention; it is responsible for filtering
        verify(notificationService).notifyUsersMentioned(
                eq(10L), any(), eq("TKT-000010"), eq(1L),
                eq(List.of(authorId)), eq(authorId), any());
    }

    // ── getById ───────────────────────────────────────────────────────────────

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

    // ── listByTicket ──────────────────────────────────────────────────────────

    @Test
    void listByTicket_returnsOrderedNotes() {
        Note n1 = buildNote(1L, 10L, NoteType.INFO, "First");
        Note n2 = buildNote(2L, 10L, NoteType.ESCALATION, "Second");
        when(noteRepository.findByTicketIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(n1, n2));

        List<Note> results = noteService.listByTicket(10L);
        assertThat(results).hasSize(2);
    }

    // ── Builders ──────────────────────────────────────────────────────────────

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

    private User buildUser(Long id, String username, String fullName, boolean active) {
        User u = new User();
        u.setUsername(username);
        u.setFullName(fullName);
        u.setIsActive(active);
        u.setEmail(username + "@test.com");
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(u, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return u;
    }

    private Ticket buildTicket(Long id, String ticketNo) {
        Ticket t = new Ticket();
        t.setTicketNo(ticketNo);
        t.setSubject("Test ticket");
        t.setStatus(TicketStatus.NEW);
        t.setPriority(TicketPriority.MEDIUM);
        t.setCustomerId(1L);
        try {
            var idField = Ticket.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(t, id);
            var pidField = Ticket.class.getDeclaredField("publicId");
            pidField.setAccessible(true);
            pidField.set(t, UUID.randomUUID());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return t;
    }
}
