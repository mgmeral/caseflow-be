package com.caseflow.email.service;

import com.caseflow.common.exception.EmailOperationException;
import com.caseflow.email.domain.EmailIngressEvent;
import com.caseflow.email.repository.EmailIngressEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the reply-to address and RFC 2822 threading headers for an outbound email.
 *
 * <p>Centralises the resolution logic that was previously duplicated between
 * {@link EmailReplyService} and the scheduled-email path. Both callers inject this
 * service and delegate to {@link #resolve}.
 *
 * <h2>Resolution rules</h2>
 * <ul>
 *   <li>If {@code sourceEventId} is provided: load the inbound event, derive {@code toAddress}
 *       from its Reply-To header (falling back to From), and build In-Reply-To / References.</li>
 *   <li>If no source event: use {@code toAddressOverride} as the recipient (proactive send).</li>
 *   <li>If neither is provided: throw {@link IllegalArgumentException}.</li>
 * </ul>
 */
@Service
public class ReplyThreadContextResolver {

    private final EmailIngressEventRepository ingressEventRepository;

    public ReplyThreadContextResolver(EmailIngressEventRepository ingressEventRepository) {
        this.ingressEventRepository = ingressEventRepository;
    }

    /**
     * Resolves threading context, enforcing that the source event belongs to the given ticket.
     *
     * <p>This is the primary method used by all production send / schedule paths.
     * It performs the same resolution as {@link #resolve} but additionally verifies
     * that the inbound event belongs to {@code ownerTicketId}, preventing cross-ticket
     * reply injection.
     *
     * @param sourceEventId    the inbound event being replied to; may be null
     * @param toAddressOverride explicit recipient for proactive sends; may be null
     * @param ownerTicketId    the ticket that must own the event; ownership check skipped if null
     * @return non-null {@link ReplyThreadContext}
     * @throws EmailOperationException with {@code SOURCE_EVENT_NOT_FOUND} when the event does not exist
     * @throws EmailOperationException with {@code SOURCE_EVENT_NOT_FOR_TICKET} when the event belongs to a different ticket
     * @throws EmailOperationException with {@code REPLY_TARGET_UNRESOLVABLE} when no recipient can be derived
     */
    @Transactional(readOnly = true)
    public ReplyThreadContext resolveForTicket(Long sourceEventId, String toAddressOverride,
                                               Long ownerTicketId) {
        if (sourceEventId != null) {
            EmailIngressEvent sourceEvent = ingressEventRepository.findById(sourceEventId)
                    .orElseThrow(() -> new EmailOperationException("SOURCE_EVENT_NOT_FOUND",
                            "Source event not found: " + sourceEventId));

            if (ownerTicketId != null && !ownerTicketId.equals(sourceEvent.getTicketId())) {
                throw new EmailOperationException("SOURCE_EVENT_NOT_FOR_TICKET",
                        "Source event " + sourceEventId
                                + " does not belong to ticket " + ownerTicketId);
            }

            String derivedTo = sourceEvent.effectiveReplyTo();
            if (derivedTo == null || derivedTo.isBlank()) {
                throw new EmailOperationException("REPLY_TARGET_UNRESOLVABLE",
                        "Cannot derive reply target: source event " + sourceEventId
                                + " has no From or Reply-To header");
            }

            String resolvedTo = extractEmailAddress(derivedTo);

            // Build RFC 2822 In-Reply-To and References headers
            String inReplyTo = sourceEvent.getMessageId();
            String priorRefs = sourceEvent.getRawReferences();
            String references;
            if (priorRefs != null && !priorRefs.isBlank()) {
                references = priorRefs.replace("|", " ").trim() + " " + sourceEvent.getMessageId();
            } else {
                references = sourceEvent.getMessageId();
            }

            return new ReplyThreadContext(resolvedTo, inReplyTo, references, sourceEventId);
        }

        if (toAddressOverride != null && !toAddressOverride.isBlank()) {
            return new ReplyThreadContext(toAddressOverride, null, null, null);
        }

        throw new EmailOperationException("REPLY_TARGET_UNRESOLVABLE",
                "Reply target cannot be determined: provide sourceEventId or toAddress");
    }

    /**
     * Resolves threading context without ticket ownership validation.
     *
     * <p>Retained for backward compatibility and internal use. Production send / schedule
     * paths should prefer {@link #resolveForTicket}.
     *
     * @param sourceEventId    the inbound {@link EmailIngressEvent} being replied to; may be null
     * @param toAddressOverride explicit recipient used when no source event is available; may be null
     * @return non-null {@link ReplyThreadContext}
     * @throws IllegalArgumentException when neither can produce a recipient address
     */
    @Transactional(readOnly = true)
    public ReplyThreadContext resolve(Long sourceEventId, String toAddressOverride) {
        if (sourceEventId != null) {
            EmailIngressEvent sourceEvent = ingressEventRepository.findById(sourceEventId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Ingress event not found: " + sourceEventId));

            String derivedTo = sourceEvent.effectiveReplyTo();
            if (derivedTo == null || derivedTo.isBlank()) {
                throw new IllegalArgumentException(
                        "Cannot derive reply target: source event " + sourceEventId
                                + " has no From or Reply-To header");
            }

            String resolvedTo = extractEmailAddress(derivedTo);

            String inReplyTo = sourceEvent.getMessageId();
            String priorRefs = sourceEvent.getRawReferences();
            String references;
            if (priorRefs != null && !priorRefs.isBlank()) {
                references = priorRefs.replace("|", " ").trim() + " " + sourceEvent.getMessageId();
            } else {
                references = sourceEvent.getMessageId();
            }

            return new ReplyThreadContext(resolvedTo, inReplyTo, references, sourceEventId);
        }

        if (toAddressOverride != null && !toAddressOverride.isBlank()) {
            return new ReplyThreadContext(toAddressOverride, null, null, null);
        }

        throw new IllegalArgumentException(
                "Reply target cannot be determined: provide sourceEventId or toAddress");
    }

    /** Strips display name: "Display Name <email@host>" → "email@host". */
    public static String extractEmailAddress(String raw) {
        if (raw == null) return null;
        int start = raw.lastIndexOf('<');
        int end = raw.lastIndexOf('>');
        if (start >= 0 && end > start) {
            return raw.substring(start + 1, end).trim();
        }
        return raw.trim();
    }
}
