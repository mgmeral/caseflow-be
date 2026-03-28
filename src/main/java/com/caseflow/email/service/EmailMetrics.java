package com.caseflow.email.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Micrometer counters for the email platform.
 * All counters use the "caseflow.email" prefix.
 */
@Component
public class EmailMetrics {

    private final Counter inboundReceived;
    private final Counter inboundProcessed;
    private final Counter inboundFailed;
    private final Counter inboundQuarantined;
    private final Counter inboundIgnored;
    private final Counter outboundQueued;
    private final Counter outboundSent;
    private final Counter outboundFailed;
    private final Counter outboundPermanentlyFailed;

    public EmailMetrics(MeterRegistry registry) {
        inboundReceived        = registry.counter("caseflow.email.inbound.received.total");
        inboundProcessed       = registry.counter("caseflow.email.inbound.processed.total");
        inboundFailed          = registry.counter("caseflow.email.inbound.failed.total");
        inboundQuarantined     = registry.counter("caseflow.email.inbound.quarantined.total");
        inboundIgnored         = registry.counter("caseflow.email.inbound.ignored.total");
        outboundQueued         = registry.counter("caseflow.email.outbound.queued.total");
        outboundSent           = registry.counter("caseflow.email.outbound.sent.total");
        outboundFailed         = registry.counter("caseflow.email.outbound.failed.total");
        outboundPermanentlyFailed = registry.counter("caseflow.email.outbound.permanently_failed.total");
    }

    public void inboundReceived() { inboundReceived.increment(); }
    public void inboundProcessed() { inboundProcessed.increment(); }
    public void inboundFailed() { inboundFailed.increment(); }
    public void inboundQuarantined() { inboundQuarantined.increment(); }
    public void inboundIgnored() { inboundIgnored.increment(); }
    public void outboundQueued() { outboundQueued.increment(); }
    public void outboundSent() { outboundSent.increment(); }
    public void outboundFailed() { outboundFailed.increment(); }
    public void outboundPermanentlyFailed() { outboundPermanentlyFailed.increment(); }
}
