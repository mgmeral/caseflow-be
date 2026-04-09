package com.caseflow.email.service;

/**
 * Resolved RFC 2822 threading context for an outbound email.
 *
 * <p>Produced by {@link ReplyThreadContextResolver} from an inbound ingress event.
 * Both immediate replies ({@link EmailReplyService}) and scheduled sends
 * ({@link com.caseflow.email.scheduled.service.ScheduledEmailService}) use this
 * to populate threading headers without duplicating resolution logic.
 *
 * @param resolvedToAddress     canonical recipient address (extracted from Reply-To or From header)
 * @param inReplyToMessageId    value for the RFC 2822 In-Reply-To header; null for proactive sends
 * @param referencesHeader      full RFC 2822 References chain (space-separated); null for proactive sends
 * @param sourceIngressEventId  the source {@link com.caseflow.email.domain.EmailIngressEvent} id; null for proactive sends
 */
public record ReplyThreadContext(
        String resolvedToAddress,
        String inReplyToMessageId,
        String referencesHeader,
        Long sourceIngressEventId
) {}
