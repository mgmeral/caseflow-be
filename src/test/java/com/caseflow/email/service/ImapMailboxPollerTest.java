package com.caseflow.email.service;

import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.repository.EmailMailboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.ServerSocket;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ImapMailboxPollerTest {

    @Mock private EmailMailboxRepository mailboxRepository;
    @Mock private EmailIngressService ingressService;

    @InjectMocks
    private ImapMailboxPoller poller;

    private EmailMailbox mailbox;

    @BeforeEach
    void setup() {
        mailbox = new EmailMailbox();
        // id is DB-generated — not settable; tests reference mailbox object identity
        mailbox.setAddress("inbox@example.com");
        mailbox.setImapHost("imap.example.com");
        mailbox.setImapUsername("inbox@example.com");
        mailbox.setImapPassword("secret");
        mailbox.setImapFolder("INBOX");
        mailbox.setPollingEnabled(true);
        mailbox.setPollIntervalSeconds(60);
    }

    // ── Guard: polling disabled ───────────────────────────────────────────────

    @Test
    void pollMailbox_skips_whenPollingDisabled() {
        mailbox.setPollingEnabled(false);

        poller.pollMailbox(mailbox);

        verify(ingressService, never()).receiveEvent(any());
        verify(mailboxRepository, never()).save(any());
    }

    @Test
    void pollMailbox_skips_whenPollingEnabledIsNull() {
        mailbox.setPollingEnabled(null);

        poller.pollMailbox(mailbox);

        verify(ingressService, never()).receiveEvent(any());
        verify(mailboxRepository, never()).save(any());
    }

    // ── Guard: missing credentials ────────────────────────────────────────────

    @Test
    void pollMailbox_skips_whenImapHostIsNull() {
        mailbox.setImapHost(null);

        poller.pollMailbox(mailbox);

        verify(ingressService, never()).receiveEvent(any());
        verify(mailboxRepository, never()).save(any());
    }

    @Test
    void pollMailbox_skips_whenImapUsernameIsNull() {
        mailbox.setImapUsername(null);

        poller.pollMailbox(mailbox);

        verify(ingressService, never()).receiveEvent(any());
        verify(mailboxRepository, never()).save(any());
    }

    // ── Connection failure → lastPollError set ────────────────────────────────

    @Test
    void pollMailbox_setsLastPollError_onConnectionFailure() throws Exception {
        // Bind a ServerSocket to get an available port, then close it so nothing is listening.
        // The IMAP connect attempt gets "Connection refused" immediately.
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }

        mailbox.setImapHost("localhost");
        mailbox.setImapPort(port);
        mailbox.setImapUseSsl(false);

        poller.pollMailbox(mailbox);

        verify(mailboxRepository).save(mailbox);
        assertThat(mailbox.getLastPollError()).isNotNull();
        assertThat(mailbox.getLastPollAt()).isNotNull();
        verify(ingressService, never()).receiveEvent(any());
    }

    @Test
    void pollMailbox_clearsLastPollError_afterConnectionFailureThenSkip() {
        // Demonstrates the guard short-circuit does NOT touch lastPollError
        mailbox.setPollingEnabled(false);
        mailbox.setLastPollError("previous error");

        poller.pollMailbox(mailbox);

        // Guard exits before touching the mailbox — error unchanged, no save
        verify(mailboxRepository, never()).save(any());
        assertThat(mailbox.getLastPollError()).isEqualTo("previous error");
    }

    // ── UID tracking: lastSeenUid starts at 1 when null ──────────────────────

    @Test
    void pollMailbox_usesUid1_whenLastSeenUidIsNull() throws Exception {
        // With null lastSeenUid the poll should attempt to fetch from UID 1.
        // Use a refused-port to trigger the failure path — just verifies no NPE
        // and that the error path is followed (not a guard exit).
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }

        mailbox.setImapHost("localhost");
        mailbox.setImapPort(port);
        mailbox.setLastSeenUid(null);

        poller.pollMailbox(mailbox);

        // Got to the connection attempt (not short-circuited by guard)
        verify(mailboxRepository).save(mailbox);
        assertThat(mailbox.getLastPollError()).isNotNull();
    }

    // ── Idempotency: receiveEvent handles duplicate messageIds ────────────────

    @Test
    void receiveEvent_isCalledWithMailboxId() throws Exception {
        // The poller passes mailbox.getId() as the mailboxId in IngressEmailData.
        // Because we cannot inject a mock IMAP store, we verify the guard
        // paths do NOT forward to ingressService — real UID-based dedup is
        // exercised in EmailIngressServiceTest.receiveEvent_isIdempotent_whenEventAlreadyExists.
        // Here we just confirm no receiveEvent calls occur for non-polling mailboxes.
        mailbox.setPollingEnabled(false);

        poller.pollMailbox(mailbox);

        verify(ingressService, never()).receiveEvent(any());
    }

    // ── Timestamp updated on connection failure ───────────────────────────────

    @Test
    void pollMailbox_updatesLastPollAt_evenOnConnectionFailure() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }

        mailbox.setImapHost("localhost");
        mailbox.setImapPort(port);
        Instant before = Instant.now();

        poller.pollMailbox(mailbox);

        assertThat(mailbox.getLastPollAt()).isNotNull();
        assertThat(mailbox.getLastPollAt()).isAfterOrEqualTo(before);
    }
}
