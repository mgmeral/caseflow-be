package com.caseflow.ticket.service;

import com.caseflow.ticket.api.dto.TagRequest;
import com.caseflow.ticket.api.dto.TagResponse;
import com.caseflow.ticket.api.dto.TicketTagResponse;
import com.caseflow.ticket.domain.Tag;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketTag;
import com.caseflow.ticket.repository.TagRepository;
import com.caseflow.ticket.repository.TicketTagRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Manages the controlled tag vocabulary and ticket-tag assignments.
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>Tag codes are normalized to UPPER_SNAKE_CASE and immutable after creation.</li>
 *   <li>Duplicate ticket-tag assignments are rejected (PK constraint + explicit guard).</li>
 *   <li>Inactive tags cannot be assigned to tickets.</li>
 *   <li>Removing a tag from a ticket removes only the link, not the tag definition.</li>
 *   <li>Tags are never hard-deleted; deactivate instead to preserve historical reports.</li>
 * </ul>
 */
@Service
public class TagService {

    private static final Logger log = LoggerFactory.getLogger(TagService.class);

    private final TagRepository tagRepository;
    private final TicketTagRepository ticketTagRepository;
    private final TicketQueryService ticketQueryService;
    private final TicketHistoryService historyService;

    public TagService(TagRepository tagRepository,
                      TicketTagRepository ticketTagRepository,
                      TicketQueryService ticketQueryService,
                      TicketHistoryService historyService) {
        this.tagRepository = tagRepository;
        this.ticketTagRepository = ticketTagRepository;
        this.ticketQueryService = ticketQueryService;
        this.historyService = historyService;
    }

    // ── Tag vocabulary CRUD ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TagResponse> listAll() {
        return tagRepository.findAllByOrderByCodeAsc().stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TagResponse> listActive() {
        return tagRepository.findAllByIsActiveTrueOrderByCodeAsc().stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TagResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public TagResponse create(TagRequest request, Long createdByUserId) {
        String normalizedCode = normalizeCode(request.code());
        if (tagRepository.existsByCode(normalizedCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tag code already exists: " + normalizedCode);
        }
        Tag tag = new Tag();
        tag.setCode(normalizedCode);
        tag.setName(request.name());
        tag.setDescription(request.description());
        tag.setColor(request.color());
        tag.setIsActive(request.isActive() != null ? request.isActive() : Boolean.TRUE);
        tag.setCreatedBy(createdByUserId);
        tag.setUpdatedBy(createdByUserId);
        Tag saved = tagRepository.save(tag);
        log.info("TAG_CRUD create — tagId: {}, code: '{}'", saved.getId(), saved.getCode());
        return toResponse(saved);
    }

    @Transactional
    public TagResponse update(Long id, TagRequest request, Long updatedByUserId) {
        Tag tag = findOrThrow(id);
        // code is immutable
        tag.setName(request.name());
        tag.setDescription(request.description());
        tag.setColor(request.color());
        if (request.isActive() != null) {
            tag.setIsActive(request.isActive());
        }
        tag.setUpdatedBy(updatedByUserId);
        Tag saved = tagRepository.save(tag);
        log.info("TAG_CRUD update — tagId: {}, code: '{}'", saved.getId(), saved.getCode());
        return toResponse(saved);
    }

    @Transactional
    public TagResponse activate(Long id, Long updatedByUserId) {
        Tag tag = findOrThrow(id);
        tag.setIsActive(Boolean.TRUE);
        tag.setUpdatedBy(updatedByUserId);
        return toResponse(tagRepository.save(tag));
    }

    @Transactional
    public TagResponse deactivate(Long id, Long updatedByUserId) {
        Tag tag = findOrThrow(id);
        tag.setIsActive(Boolean.FALSE);
        tag.setUpdatedBy(updatedByUserId);
        return toResponse(tagRepository.save(tag));
    }

    // ── Ticket-tag assignment ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TicketTagResponse> listTicketTags(Long ticketId) {
        ticketQueryService.getById(ticketId);  // validates ticket exists
        List<TicketTag> links = ticketTagRepository.findByTicketId(ticketId);
        return links.stream().map(tt -> {
            Tag tag = findOrThrow(tt.getTagId());
            return toTicketTagResponse(tt, tag);
        }).toList();
    }

    @Transactional
    public TicketTagResponse addTagToTicket(Long ticketId, Long tagId, Long actorUserId) {
        ticketQueryService.getById(ticketId);  // validates ticket exists
        Tag tag = findOrThrow(tagId);

        if (!Boolean.TRUE.equals(tag.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tag " + tag.getCode() + " is inactive and cannot be assigned");
        }
        if (ticketTagRepository.existsByTicketIdAndTagId(ticketId, tagId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tag " + tag.getCode() + " is already assigned to this ticket");
        }

        TicketTag link = new TicketTag();
        link.setTicketId(ticketId);
        link.setTagId(tagId);
        link.setTaggedBy(actorUserId);
        ticketTagRepository.save(link);

        Ticket ticket = ticketQueryService.getById(ticketId);
        historyService.recordTagAdded(ticketId, ticket.getPublicId(), tagId, tag.getCode(), actorUserId);
        log.info("TICKET_TAG add — ticketId: {}, tagId: {}, code: '{}'", ticketId, tagId, tag.getCode());

        return toTicketTagResponse(link, tag);
    }

    @Transactional
    public void removeTagFromTicket(Long ticketId, Long tagId, Long actorUserId) {
        ticketQueryService.getById(ticketId);  // validates ticket exists
        Tag tag = findOrThrow(tagId);

        if (!ticketTagRepository.existsByTicketIdAndTagId(ticketId, tagId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Tag " + tag.getCode() + " is not assigned to this ticket");
        }

        ticketTagRepository.deleteByTicketIdAndTagId(ticketId, tagId);

        Ticket ticket = ticketQueryService.getById(ticketId);
        historyService.recordTagRemoved(ticketId, ticket.getPublicId(), tagId, tag.getCode(), actorUserId);
        log.info("TICKET_TAG remove — ticketId: {}, tagId: {}, code: '{}'", ticketId, tagId, tag.getCode());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Tag findOrThrow(Long id) {
        return tagRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Tag not found: " + id));
    }

    private String normalizeCode(String code) {
        return code != null ? code.trim().toUpperCase() : null;
    }

    private TagResponse toResponse(Tag tag) {
        return new TagResponse(
                tag.getId(),
                tag.getCode(),
                tag.getName(),
                tag.getDescription(),
                tag.getColor(),
                Boolean.TRUE.equals(tag.getIsActive()),
                tag.getCreatedAt(),
                tag.getUpdatedAt()
        );
    }

    private TicketTagResponse toTicketTagResponse(TicketTag tt, Tag tag) {
        return new TicketTagResponse(
                tag.getId(),
                tag.getCode(),
                tag.getName(),
                tag.getColor(),
                tt.getTaggedAt(),
                tt.getTaggedBy()
        );
    }
}
