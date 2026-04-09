package com.caseflow.note.api.mapper;

import com.caseflow.identity.domain.User;
import com.caseflow.identity.repository.UserRepository;
import com.caseflow.note.api.dto.NoteResponse;
import com.caseflow.note.api.dto.NoteSummaryResponse;
import com.caseflow.note.api.dto.UserSummary;
import com.caseflow.note.domain.Note;
import com.caseflow.note.domain.NoteMention;
import com.caseflow.note.repository.NoteMentionRepository;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps {@link Note} entities to API response DTOs, enriching with author summary and mentions.
 *
 * <p>Replaced the MapStruct interface because enrichment requires injected repositories.
 * The createdBy (numeric id) field is preserved for backward compatibility alongside
 * the new createdByUser summary object.
 */
@Component
public class NoteMapper {

    private final UserRepository userRepository;
    private final NoteMentionRepository noteMentionRepository;

    public NoteMapper(UserRepository userRepository, NoteMentionRepository noteMentionRepository) {
        this.userRepository = userRepository;
        this.noteMentionRepository = noteMentionRepository;
    }

    public NoteResponse toResponse(Note note) {
        UserSummary author = resolveUser(note.getCreatedBy());
        List<NoteMention> mentions = noteMentionRepository.findByNoteId(note.getId());
        List<UserSummary> mentionSummaries = resolveMentionSummaries(mentions);

        return new NoteResponse(
                note.getId(),
                note.getTicketId(),
                note.getContent(),
                note.getType(),
                note.getCreatedBy(),
                author,
                mentionSummaries,
                note.getCreatedAt()
        );
    }

    public NoteSummaryResponse toSummaryResponse(Note note) {
        return new NoteSummaryResponse(
                note.getId(),
                note.getTicketId(),
                note.getType(),
                note.getCreatedBy(),
                note.getCreatedAt()
        );
    }

    public List<NoteResponse> toResponseList(List<Note> notes) {
        if (notes.isEmpty()) return Collections.emptyList();

        // Batch-fetch authors
        Set<Long> authorIds = notes.stream().map(Note::getCreatedBy).collect(Collectors.toSet());
        Map<Long, User> authors = userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // Batch-fetch all mentions for these notes
        List<Long> noteIds = notes.stream().map(Note::getId).toList();
        List<NoteMention> allMentions = noteMentionRepository.findByNoteIdIn(noteIds);
        Map<Long, List<NoteMention>> mentionsByNoteId = allMentions.stream()
                .collect(Collectors.groupingBy(NoteMention::getNoteId));

        // Batch-fetch mention users
        Set<Long> mentionUserIds = allMentions.stream().map(NoteMention::getUserId).collect(Collectors.toSet());
        Map<Long, User> mentionUsers = userRepository.findAllById(mentionUserIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return notes.stream().map(note -> {
            User author = authors.get(note.getCreatedBy());
            UserSummary authorSummary = author != null ? toUserSummary(author) : null;
            List<NoteMention> noteMentions = mentionsByNoteId.getOrDefault(note.getId(), List.of());
            List<UserSummary> mentionSummaries = noteMentions.stream()
                    .map(m -> {
                        User u = mentionUsers.get(m.getUserId());
                        return u != null ? toUserSummary(u) : null;
                    })
                    .filter(s -> s != null)
                    .toList();

            return new NoteResponse(
                    note.getId(),
                    note.getTicketId(),
                    note.getContent(),
                    note.getType(),
                    note.getCreatedBy(),
                    authorSummary,
                    mentionSummaries,
                    note.getCreatedAt()
            );
        }).toList();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private UserSummary resolveUser(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId).map(this::toUserSummary).orElse(null);
    }

    private List<UserSummary> resolveMentionSummaries(List<NoteMention> mentions) {
        if (mentions.isEmpty()) return Collections.emptyList();
        Set<Long> ids = mentions.stream().map(NoteMention::getUserId).collect(Collectors.toSet());
        Map<Long, User> users = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return mentions.stream()
                .map(m -> users.containsKey(m.getUserId()) ? toUserSummary(users.get(m.getUserId())) : null)
                .filter(s -> s != null)
                .toList();
    }

    private UserSummary toUserSummary(User user) {
        return new UserSummary(user.getId(), user.getUsername(), user.getFullName());
    }
}
