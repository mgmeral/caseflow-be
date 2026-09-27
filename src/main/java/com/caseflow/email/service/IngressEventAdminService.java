package com.caseflow.email.service;

import com.caseflow.common.exception.IngressEventNotFoundException;
import com.caseflow.common.exception.InvalidIngressEventStateException;
import com.caseflow.email.domain.EmailIngressEvent;
import com.caseflow.email.domain.IngressEventStatus;
import com.caseflow.email.repository.EmailIngressEventRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Operator recovery operations for inbound ingress events.
 *
 * <p>These operations allow an operator to inspect, manually trigger, quarantine,
 * or release stuck / failed events without modifying the automatic retry scheduler.
 */
@Service
public class IngressEventAdminService {

    private static final Logger log = LoggerFactory.getLogger(IngressEventAdminService.class);

    private final EmailIngressEventRepository eventRepository;
    private final EmailIngressService ingressService;

    public IngressEventAdminService(EmailIngressEventRepository eventRepository,
                                     EmailIngressService ingressService) {
        this.eventRepository = eventRepository;
        this.ingressService = ingressService;
    }

    /**
     * Lists ingress events matching the given filters. All filter fields are optional.
     */
    @Transactional(readOnly = true)
    public Page<EmailIngressEvent> findFiltered(IngressEventFilter filter, Pageable pageable) {
        return eventRepository.findAll(toSpecification(filter), pageable);
    }

    /**
     * Number of events per status under the filter's non-status criteria, so the counts
     * describe the same slice of events the list shows. Every status is present (0 if none).
     */
    @Transactional(readOnly = true)
    public Map<IngressEventStatus, Long> countByStatus(IngressEventFilter filter) {
        IngressEventFilter base = filter.withoutStatuses();
        Map<IngressEventStatus, Long> counts = new EnumMap<>(IngressEventStatus.class);
        for (IngressEventStatus status : IngressEventStatus.values()) {
            IngressEventFilter one = new IngressEventFilter(List.of(status), base.mailboxId(),
                    base.messageId(), base.ticketId(), base.q(), base.from(), base.to());
            counts.put(status, eventRepository.count(toSpecification(one)));
        }
        return counts;
    }

    // Built as a Specification (rather than a JPQL "(:param IS NULL OR ...)" query) so that
    // an absent filter never binds a null parameter at all — Postgres/PgJDBC cannot always
    // infer the SQL type of a bare null parameter used only in an "IS NULL" check (observed
    // for the Instant from/to bounds: "could not determine data type of parameter $9").
    private static Specification<EmailIngressEvent> toSpecification(IngressEventFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!f.statuses().isEmpty()) predicates.add(root.get("status").in(f.statuses()));
            if (f.mailboxId() != null) predicates.add(cb.equal(root.get("mailboxId"), f.mailboxId()));
            if (f.messageId() != null) predicates.add(cb.equal(root.get("messageId"), f.messageId()));
            if (f.ticketId() != null) predicates.add(cb.equal(root.get("ticketId"), f.ticketId()));
            if (f.from() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("receivedAt"), f.from()));
            if (f.to() != null) predicates.add(cb.lessThanOrEqualTo(root.get("receivedAt"), f.to()));
            if (f.q() != null) {
                String pattern = "%" + escapeLike(f.q().toLowerCase(Locale.ROOT)) + "%";
                List<Predicate> text = new ArrayList<>(List.of(
                        cb.like(cb.lower(root.get("rawFrom")), pattern, '\\'),
                        cb.like(cb.lower(root.get("rawSubject")), pattern, '\\'),
                        cb.like(cb.lower(root.get("messageId")), pattern, '\\')));
                if (f.q().matches("\\d{1,18}")) {
                    text.add(cb.equal(root.get("ticketId"), Long.parseLong(f.q())));
                }
                predicates.add(cb.or(text.toArray(new Predicate[0])));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * Loads a single event by id; throws if not found.
     */
    @Transactional(readOnly = true)
    public EmailIngressEvent getById(Long id) {
        return findOrThrow(id);
    }

    /**
     * Manually triggers Stage-2 processing for the event.
     * Works for events in RECEIVED, FAILED, or QUARANTINED state.
     * QUARANTINED events are released to RECEIVED first, then processed.
     *
     * @throws InvalidIngressEventStateException when the event is already PROCESSED (terminal)
     */
    @Transactional
    public void processEvent(Long eventId) {
        EmailIngressEvent event = findOrThrow(eventId);
        log.info("ADMIN_PROCESS eventId: {}, currentStatus: {}", eventId, event.getStatus());
        if (event.getStatus() == IngressEventStatus.PROCESSED) {
            throw new InvalidIngressEventStateException(eventId, "processed manually",
                    event.getStatus());
        }
        if (event.getStatus() == IngressEventStatus.QUARANTINED) {
            ingressService.releaseEvent(eventId);
        }
        ingressService.processEvent(eventId);
    }

    /**
     * Resets a FAILED event to RECEIVED and triggers immediate processing.
     * Provides an instant retry without waiting for the retry scheduler.
     *
     * @throws InvalidIngressEventStateException when the event is not in FAILED state
     */
    @Transactional
    public void retryEvent(Long eventId) {
        EmailIngressEvent event = findOrThrow(eventId);
        if (event.getStatus() != IngressEventStatus.FAILED) {
            throw new InvalidIngressEventStateException(eventId, "retried",
                    event.getStatus(), IngressEventStatus.FAILED);
        }
        log.info("ADMIN_RETRY eventId: {}", eventId);
        ingressService.processEvent(eventId);
    }

    /**
     * Moves an event to QUARANTINED with an explicit reason.
     * Quarantined events are not picked up by the retry scheduler.
     *
     * @throws InvalidIngressEventStateException when the event is already PROCESSED (terminal)
     */
    @Transactional
    public void quarantineEvent(Long eventId, String reason) {
        EmailIngressEvent event = findOrThrow(eventId);
        if (event.getStatus() == IngressEventStatus.PROCESSED) {
            throw new InvalidIngressEventStateException(eventId, "quarantined",
                    event.getStatus());
        }
        log.info("ADMIN_QUARANTINE eventId: {}, reason: '{}'", eventId, reason);
        ingressService.quarantineEvent(eventId, reason);
    }

    /**
     * Releases a QUARANTINED event back to RECEIVED so the retry scheduler picks it up.
     *
     * @throws InvalidIngressEventStateException when the event is not in QUARANTINED state
     */
    @Transactional
    public void releaseEvent(Long eventId) {
        EmailIngressEvent event = findOrThrow(eventId);
        if (event.getStatus() != IngressEventStatus.QUARANTINED) {
            throw new InvalidIngressEventStateException(eventId, "released",
                    event.getStatus(), IngressEventStatus.QUARANTINED);
        }
        log.info("ADMIN_RELEASE eventId: {}", eventId);
        ingressService.releaseEvent(eventId);
    }

    private EmailIngressEvent findOrThrow(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new IngressEventNotFoundException(id));
    }
}
