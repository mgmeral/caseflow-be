package com.caseflow.email.service;

import com.caseflow.common.exception.EmailOperationException;
import com.caseflow.email.domain.EmailIngressEvent;
import com.caseflow.email.repository.EmailIngressEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplyThreadContextResolverTest {

    @Mock private EmailIngressEventRepository ingressEventRepository;

    @InjectMocks
    private ReplyThreadContextResolver resolver;

    private EmailIngressEvent event;

    @BeforeEach
    void setUp() {
        event = new EmailIngressEvent();
        event.setRawFrom("customer@example.com");
        event.setMessageId("<msg1@example.com>");
        event.setReceivedAt(Instant.now());
        event.setTicketId(42L);
        // id is set by DB; tests reference the event via mocked repo (findById stub)
    }

    // ── resolveForTicket — ownership ──────────────────────────────────────────

    @Test
    void resolveForTicket_succeeds_whenEventBelongsToTicket() {
        when(ingressEventRepository.findById(10L)).thenReturn(Optional.of(event));

        ReplyThreadContext ctx = resolver.resolveForTicket(10L, null, 42L);

        assertThat(ctx.resolvedToAddress()).isEqualTo("customer@example.com");
        assertThat(ctx.sourceIngressEventId()).isEqualTo(10L);
        assertThat(ctx.inReplyToMessageId()).isEqualTo("<msg1@example.com>");
    }

    @Test
    void resolveForTicket_throwsSourceEventNotForTicket_whenEventBelongsToDifferentTicket() {
        when(ingressEventRepository.findById(10L)).thenReturn(Optional.of(event));
        // event belongs to ticket 42, but we claim ticket 99
        assertThatThrownBy(() -> resolver.resolveForTicket(10L, null, 99L))
                .isInstanceOf(EmailOperationException.class)
                .hasMessageContaining("does not belong to ticket 99")
                .extracting(e -> ((EmailOperationException) e).getCode())
                .isEqualTo("SOURCE_EVENT_NOT_FOR_TICKET");
    }

    @Test
    void resolveForTicket_throwsSourceEventNotFound_whenEventMissing() {
        when(ingressEventRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveForTicket(999L, null, 42L))
                .isInstanceOf(EmailOperationException.class)
                .extracting(e -> ((EmailOperationException) e).getCode())
                .isEqualTo("SOURCE_EVENT_NOT_FOUND");
    }

    @Test
    void resolveForTicket_skipsOwnershipCheck_whenOwnerTicketIdIsNull() {
        when(ingressEventRepository.findById(10L)).thenReturn(Optional.of(event));

        // null ownerTicketId → no ownership check, any ticket's event is accepted
        ReplyThreadContext ctx = resolver.resolveForTicket(10L, null, null);

        assertThat(ctx.resolvedToAddress()).isEqualTo("customer@example.com");
    }

    @Test
    void resolveForTicket_throwsReplyTargetUnresolvable_whenNeitherSourceNorToAddress() {
        assertThatThrownBy(() -> resolver.resolveForTicket(null, null, 42L))
                .isInstanceOf(EmailOperationException.class)
                .extracting(e -> ((EmailOperationException) e).getCode())
                .isEqualTo("REPLY_TARGET_UNRESOLVABLE");
    }

    @Test
    void resolveForTicket_usesToAddressOverride_whenNoSourceEventId() {
        ReplyThreadContext ctx = resolver.resolveForTicket(null, "manual@example.com", 42L);

        assertThat(ctx.resolvedToAddress()).isEqualTo("manual@example.com");
        assertThat(ctx.inReplyToMessageId()).isNull();
        assertThat(ctx.referencesHeader()).isNull();
        assertThat(ctx.sourceIngressEventId()).isNull();
    }

    @Test
    void resolveForTicket_usesReplyToHeader_whenPresent() {
        event.setRawReplyTo("reply@bigcorp.com");
        when(ingressEventRepository.findById(10L)).thenReturn(Optional.of(event));

        ReplyThreadContext ctx = resolver.resolveForTicket(10L, null, 42L);

        assertThat(ctx.resolvedToAddress()).isEqualTo("reply@bigcorp.com");
    }

    @Test
    void resolveForTicket_stripsDisplayName_fromReplyToHeader() {
        event.setRawReplyTo("John Doe <john@bigcorp.com>");
        when(ingressEventRepository.findById(10L)).thenReturn(Optional.of(event));

        ReplyThreadContext ctx = resolver.resolveForTicket(10L, null, 42L);

        assertThat(ctx.resolvedToAddress()).isEqualTo("john@bigcorp.com");
    }

    @Test
    void resolveForTicket_buildsReferencesChain_fromPriorReferences() {
        event.setRawReferences("<a@ex.com>|<b@ex.com>");
        when(ingressEventRepository.findById(10L)).thenReturn(Optional.of(event));

        ReplyThreadContext ctx = resolver.resolveForTicket(10L, null, 42L);

        assertThat(ctx.referencesHeader())
                .isEqualTo("<a@ex.com> <b@ex.com> <msg1@example.com>");
    }

    @Test
    void resolveForTicket_throwsReplyTargetUnresolvable_whenNoFromOrReplyTo() {
        event.setRawFrom(null);
        event.setRawReplyTo(null);
        when(ingressEventRepository.findById(10L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> resolver.resolveForTicket(10L, null, 42L))
                .isInstanceOf(EmailOperationException.class)
                .extracting(e -> ((EmailOperationException) e).getCode())
                .isEqualTo("REPLY_TARGET_UNRESOLVABLE");
    }
}
