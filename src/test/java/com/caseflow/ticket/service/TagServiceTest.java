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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock TagRepository tagRepository;
    @Mock TicketTagRepository ticketTagRepository;
    @Mock TicketQueryService ticketQueryService;
    @Mock TicketHistoryService historyService;

    @InjectMocks TagService tagService;

    // ── Tag CRUD ──────────────────────────────────────────────────────────────

    @Test
    void create_normalizesCodeToUppercase() {
        when(tagRepository.existsByCode("BUG_REPORT")).thenReturn(false);
        Tag saved = tag(1L, "BUG_REPORT", "Bug Report", true);
        when(tagRepository.save(any())).thenReturn(saved);

        TagResponse result = tagService.create(
                new TagRequest("bug_report", "Bug Report", null, null, true), 1L);

        assertThat(result.code()).isEqualTo("BUG_REPORT");
    }

    @Test
    void create_rejectsUniqueCodeViolation() {
        when(tagRepository.existsByCode("BUG")).thenReturn(true);

        assertThatThrownBy(() -> tagService.create(
                new TagRequest("BUG", "Bug", null, null, true), 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void deactivate_setsIsActiveFalse() {
        Tag existing = tag(1L, "OLD", "Old Tag", true);
        when(tagRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TagResponse result = tagService.deactivate(1L, 99L);

        assertThat(result.isActive()).isFalse();
    }

    // ── Ticket-tag assignment ─────────────────────────────────────────────────

    @Test
    void addTagToTicket_createsLinkAndRecordsHistory() {
        Ticket ticket = ticket(10L);
        Tag activeTag = tag(5L, "VIP", "VIP Customer", true);

        when(ticketQueryService.getById(10L)).thenReturn(ticket);
        when(tagRepository.findById(5L)).thenReturn(Optional.of(activeTag));
        when(ticketTagRepository.existsByTicketIdAndTagId(10L, 5L)).thenReturn(false);
        when(ticketTagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TicketTagResponse result = tagService.addTagToTicket(10L, 5L, 99L);

        assertThat(result.tagCode()).isEqualTo("VIP");
        verify(historyService).recordTagAdded(anyLong(), any(), anyLong(), anyString(), anyLong());
    }

    @Test
    void addTagToTicket_rejectsDuplicate() {
        Ticket ticket = ticket(10L);
        Tag activeTag = tag(5L, "VIP", "VIP", true);

        when(ticketQueryService.getById(10L)).thenReturn(ticket);
        when(tagRepository.findById(5L)).thenReturn(Optional.of(activeTag));
        when(ticketTagRepository.existsByTicketIdAndTagId(10L, 5L)).thenReturn(true);

        assertThatThrownBy(() -> tagService.addTagToTicket(10L, 5L, 99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already assigned");
    }

    @Test
    void addTagToTicket_rejectsInactiveTag() {
        Ticket ticket = ticket(10L);
        Tag inactiveTag = tag(5L, "OLD", "Old", false);

        when(ticketQueryService.getById(10L)).thenReturn(ticket);
        when(tagRepository.findById(5L)).thenReturn(Optional.of(inactiveTag));

        assertThatThrownBy(() -> tagService.addTagToTicket(10L, 5L, 99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void removeTagFromTicket_removesLinkAndRecordsHistory() {
        Ticket ticket = ticket(10L);
        Tag existing = tag(5L, "VIP", "VIP", true);

        when(ticketQueryService.getById(10L)).thenReturn(ticket);
        when(tagRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(ticketTagRepository.existsByTicketIdAndTagId(10L, 5L)).thenReturn(true);

        tagService.removeTagFromTicket(10L, 5L, 99L);

        verify(ticketTagRepository).deleteByTicketIdAndTagId(10L, 5L);
        verify(historyService).recordTagRemoved(anyLong(), any(), anyLong(), anyString(), anyLong());
    }

    @Test
    void removeTagFromTicket_throwsWhenNotAssigned() {
        Ticket ticket = ticket(10L);
        Tag existing = tag(5L, "VIP", "VIP", true);

        when(ticketQueryService.getById(10L)).thenReturn(ticket);
        when(tagRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(ticketTagRepository.existsByTicketIdAndTagId(10L, 5L)).thenReturn(false);

        assertThatThrownBy(() -> tagService.removeTagFromTicket(10L, 5L, 99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not assigned");

        verify(ticketTagRepository, never()).deleteByTicketIdAndTagId(anyLong(), anyLong());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Tag tag(Long id, String code, String name, boolean active) {
        Tag t = new Tag();
        t.setCode(code);
        t.setName(name);
        t.setIsActive(active);
        // Reflectively set id for test purposes
        try {
            var f = Tag.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(t, id);
            var ca = Tag.class.getDeclaredField("createdAt");
            ca.setAccessible(true);
            ca.set(t, Instant.now());
            var ua = Tag.class.getDeclaredField("updatedAt");
            ua.setAccessible(true);
            ua.set(t, Instant.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return t;
    }

    private Ticket ticket(Long id) {
        Ticket t = new Ticket();
        try {
            var f = Ticket.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(t, id);
            var pf = Ticket.class.getDeclaredField("publicId");
            pf.setAccessible(true);
            pf.set(t, UUID.randomUUID());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return t;
    }
}
