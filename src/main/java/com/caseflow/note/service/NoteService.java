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
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NoteService {

    private static final Logger log = LoggerFactory.getLogger(NoteService.class);

    /** Maximum number of distinct user mentions per note. */
    private static final int MAX_MENTIONS = 20;

    private final NoteRepository noteRepository;
    private final TicketHistoryService ticketHistoryService;
    private final NoteMentionRepository noteMentionRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final NotificationService notificationService;

    public NoteService(NoteRepository noteRepository,
                       TicketHistoryService ticketHistoryService,
                       NoteMentionRepository noteMentionRepository,
                       UserRepository userRepository,
                       TicketRepository ticketRepository,
                       NotificationService notificationService) {
        this.noteRepository = noteRepository;
        this.ticketHistoryService = ticketHistoryService;
        this.noteMentionRepository = noteMentionRepository;
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
        this.notificationService = notificationService;
    }

    /**
     * Adds a note to a ticket, optionally persisting structured @mentions for INTERNAL notes.
     *
     * <p>Mention rules (INTERNAL type only):
     * <ul>
     *   <li>Duplicate user ids are deduplicated silently</li>
     *   <li>Max {@value #MAX_MENTIONS} distinct mentions per note</li>
     *   <li>Mentioned users must exist and be active</li>
     *   <li>Self-mention is persisted but produces no notification</li>
     * </ul>
     *
     * <p><b>Ticket-access note:</b> mentioned users are validated as active users but are NOT checked
     * for ticket-read access. A user who cannot view the ticket may still receive a mention notification.
     * This is intentional for the current product model where mention notifications serve as an explicit
     * invite signal. Ticket-scoped access validation for mentions should be added if the product
     * requires strict access gating before notification dispatch.
     *
     * @param ticketId          ticket to attach the note to
     * @param content           note text (may contain @mentions for display, but backend uses mentionedUserIds)
     * @param type              note type; mentions only processed for INTERNAL
     * @param createdBy         user creating the note
     * @param mentionedUserIds  explicit list of mentioned user ids (nullable / empty = no mentions)
     */
    @Transactional
    public Note addNote(Long ticketId, String content, NoteType type, Long createdBy,
                        List<Long> mentionedUserIds) {
        log.info("Adding {} note to ticket {} — createdBy: {}", type, ticketId, createdBy);

        Note note = new Note();
        note.setTicketId(ticketId);
        note.setContent(content);
        note.setType(type);
        note.setCreatedBy(createdBy);
        Note saved = noteRepository.save(note);
        ticketHistoryService.recordNoteAdded(ticketId, createdBy);

        // Mentions are only valid for INTERNAL notes
        if (type == NoteType.INTERNAL && mentionedUserIds != null && !mentionedUserIds.isEmpty()) {
            List<Long> uniqueIds = deduplicate(mentionedUserIds);
            List<Long> validIds = validateMentionedUsers(uniqueIds, saved.getId());
            persistMentions(saved.getId(), validIds);
            dispatchMentionNotifications(saved.getId(), ticketId, validIds, createdBy);
        }

        log.info("Note {} added to ticket {}", saved.getId(), ticketId);
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

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Deduplicates and limits the mention list to MAX_MENTIONS.
     * Uses LinkedHashSet to preserve first-occurrence order.
     */
    private List<Long> deduplicate(List<Long> ids) {
        List<Long> unique = new ArrayList<>(new LinkedHashSet<>(ids));
        if (unique.size() > MAX_MENTIONS) {
            log.warn("Mention list truncated from {} to {}", unique.size(), MAX_MENTIONS);
            return unique.subList(0, MAX_MENTIONS);
        }
        return unique;
    }

    /**
     * Validates that each id corresponds to an active user.
     * Invalid or inactive users are dropped with a warning.
     */
    private List<Long> validateMentionedUsers(List<Long> ids, Long noteId) {
        List<User> found = userRepository.findAllById(ids);
        Map<Long, User> byId = found.stream().collect(Collectors.toMap(User::getId, u -> u));

        List<Long> valid = new ArrayList<>();
        for (Long id : ids) {
            User u = byId.get(id);
            if (u == null) {
                log.warn("Note {} mention skipped — userId {} not found", noteId, id);
            } else if (!Boolean.TRUE.equals(u.getIsActive())) {
                log.warn("Note {} mention skipped — userId {} is inactive", noteId, id);
            } else {
                valid.add(id);
            }
        }
        return valid;
    }

    private void persistMentions(Long noteId, List<Long> userIds) {
        List<NoteMention> mentions = userIds.stream().map(userId -> {
            NoteMention m = new NoteMention();
            m.setNoteId(noteId);
            m.setUserId(userId);
            return m;
        }).toList();
        noteMentionRepository.saveAll(mentions);
        log.info("Persisted {} mention(s) for note {}", mentions.size(), noteId);
    }

    private void dispatchMentionNotifications(Long noteId, Long ticketId, List<Long> userIds,
                                              Long actorUserId) {
        ticketRepository.findById(ticketId).ifPresent(ticket -> {
            User actor = userRepository.findById(actorUserId).orElse(null);
            String actorDisplayName = actor != null
                    ? (actor.getFullName() != null ? actor.getFullName() : actor.getUsername())
                    : "Someone";

            notificationService.notifyUsersMentioned(
                    ticketId, ticket.getPublicId(), ticket.getTicketNo(),
                    noteId, userIds, actorUserId, actorDisplayName);
        });
    }
}
