package com.caseflow.ticket.service;

import com.caseflow.customer.repository.CustomerRepository;
import com.caseflow.identity.repository.GroupRepository;
import com.caseflow.identity.repository.UserRepository;
import com.caseflow.storage.service.AttachmentService;
import com.caseflow.ticket.api.dto.AttachmentMetadataResponse;
import com.caseflow.ticket.api.dto.HistorySummaryResponse;
import com.caseflow.ticket.api.dto.TicketDetailResponse;
import com.caseflow.ticket.api.dto.TicketResponse;
import com.caseflow.ticket.api.dto.TicketSummaryResponse;
import com.caseflow.ticket.api.mapper.AttachmentMetadataMapper;
import com.caseflow.ticket.api.mapper.HistoryMapper;
import com.caseflow.ticket.domain.History;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.HistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TicketReadService {

    private final TicketQueryService ticketQueryService;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final AttachmentService attachmentService;
    private final AttachmentMetadataMapper attachmentMetadataMapper;
    private final HistoryRepository historyRepository;
    private final HistoryMapper historyMapper;

    public TicketReadService(TicketQueryService ticketQueryService,
                             CustomerRepository customerRepository,
                             UserRepository userRepository,
                             GroupRepository groupRepository,
                             AttachmentService attachmentService,
                             AttachmentMetadataMapper attachmentMetadataMapper,
                             HistoryRepository historyRepository,
                             HistoryMapper historyMapper) {
        this.ticketQueryService = ticketQueryService;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.attachmentService = attachmentService;
        this.attachmentMetadataMapper = attachmentMetadataMapper;
        this.historyRepository = historyRepository;
        this.historyMapper = historyMapper;
    }

    @Transactional(readOnly = true)
    public TicketResponse getResponse(Long ticketId) {
        return toResponse(ticketQueryService.getById(ticketId));
    }

    @Transactional(readOnly = true)
    public TicketResponse getResponseByTicketNo(String ticketNo) {
        return toResponse(ticketQueryService.getByTicketNo(ticketNo));
    }

    @Transactional(readOnly = true)
    public Page<TicketSummaryResponse> search(TicketStatus status, TicketPriority priority,
                                              Long assignedUserId, Long assignedGroupId,
                                              Long customerId, String searchText,
                                              Instant from, Instant to,
                                              Specification<Ticket> scopeSpec, Pageable pageable) {
        Page<Ticket> page = ticketQueryService.search(
                status, priority, assignedUserId, assignedGroupId, customerId, searchText, from, to,
                scopeSpec, pageable);
        return enrichPage(page);
    }

    @Transactional(readOnly = true)
    public Page<TicketSummaryResponse> searchScoped(Specification<Ticket> spec, Pageable pageable) {
        return enrichPage(ticketQueryService.searchWithSpec(spec, pageable));
    }

    @Transactional(readOnly = true)
    public TicketDetailResponse getDetail(Long ticketId) {
        Ticket ticket = ticketQueryService.getById(ticketId);

        List<AttachmentMetadataResponse> attachments =
                attachmentMetadataMapper.toResponseList(attachmentService.findByTicketId(ticketId));

        List<History> historyEntities = historyRepository.findByTicketIdOrderByPerformedAtAsc(ticketId);
        Set<Long> performerIds = historyEntities.stream()
                .filter(h -> h.getPerformedBy() != null)
                .map(History::getPerformedBy)
                .collect(Collectors.toSet());
        Map<Long, String> performerNames = batchFetchUserNames(performerIds);

        List<HistorySummaryResponse> history = historyEntities.stream()
                .map(h -> {
                    HistorySummaryResponse base = historyMapper.toSummaryResponse(h);
                    return new HistorySummaryResponse(
                            base.id(),
                            base.actionType(),
                            base.performedBy(),
                            h.getPerformedBy() != null ? performerNames.get(h.getPerformedBy()) : null,
                            base.sourceType(),
                            base.summary(),
                            base.performedAt()
                    );
                })
                .toList();

        return new TicketDetailResponse(
                ticket.getId(),
                ticket.getPublicId(),
                ticket.getTicketNo(),
                ticket.getSubject(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getCustomerId(),
                resolveCustomerName(ticket.getCustomerId()),
                ticket.getAssignedUserId(),
                resolveUserName(ticket.getAssignedUserId()),
                ticket.getAssignedGroupId(),
                resolveGroupName(ticket.getAssignedGroupId()),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getClosedAt(),
                attachments,
                history
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private TicketResponse toResponse(Ticket t) {
        return new TicketResponse(
                t.getId(),
                t.getPublicId(),
                t.getTicketNo(),
                t.getSubject(),
                t.getDescription(),
                t.getStatus(),
                t.getPriority(),
                t.getCustomerId(),
                resolveCustomerName(t.getCustomerId()),
                t.getAssignedUserId(),
                resolveUserName(t.getAssignedUserId()),
                t.getAssignedGroupId(),
                resolveGroupName(t.getAssignedGroupId()),
                t.getCreatedAt(),
                t.getUpdatedAt(),
                t.getClosedAt()
        );
    }

    private Page<TicketSummaryResponse> enrichPage(Page<Ticket> page) {
        List<Ticket> tickets = page.getContent();

        Set<Long> customerIds = tickets.stream()
                .filter(t -> t.getCustomerId() != null)
                .map(Ticket::getCustomerId)
                .collect(Collectors.toSet());
        Set<Long> userIds = tickets.stream()
                .filter(t -> t.getAssignedUserId() != null)
                .map(Ticket::getAssignedUserId)
                .collect(Collectors.toSet());
        Set<Long> groupIds = tickets.stream()
                .filter(t -> t.getAssignedGroupId() != null)
                .map(Ticket::getAssignedGroupId)
                .collect(Collectors.toSet());

        Map<Long, String> customerNames = batchFetchCustomerNames(customerIds);
        Map<Long, String> userNames = batchFetchUserNames(userIds);
        Map<Long, String> groupNames = batchFetchGroupNames(groupIds);

        return page.map(t -> new TicketSummaryResponse(
                t.getId(),
                t.getPublicId(),
                t.getTicketNo(),
                t.getSubject(),
                t.getStatus(),
                t.getPriority(),
                t.getCustomerId(),
                t.getCustomerId() != null ? customerNames.get(t.getCustomerId()) : null,
                t.getAssignedUserId(),
                t.getAssignedUserId() != null ? userNames.get(t.getAssignedUserId()) : null,
                t.getAssignedGroupId(),
                t.getAssignedGroupId() != null ? groupNames.get(t.getAssignedGroupId()) : null,
                t.getCreatedAt(),
                t.getUpdatedAt()
        ));
    }

    private Map<Long, String> batchFetchCustomerNames(Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return customerRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(c -> c.getId(), c -> c.getName()));
    }

    private Map<Long, String> batchFetchUserNames(Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(u -> u.getId(), u -> u.getUsername()));
    }

    private Map<Long, String> batchFetchGroupNames(Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return groupRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(g -> g.getId(), g -> g.getName()));
    }

    private String resolveCustomerName(Long id) {
        if (id == null) return null;
        return customerRepository.findById(id).map(c -> c.getName()).orElse(null);
    }

    private String resolveUserName(Long id) {
        if (id == null) return null;
        return userRepository.findById(id).map(u -> u.getUsername()).orElse(null);
    }

    private String resolveGroupName(Long id) {
        if (id == null) return null;
        return groupRepository.findById(id).map(g -> g.getName()).orElse(null);
    }
}
