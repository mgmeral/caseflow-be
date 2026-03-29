package com.caseflow.email.service;

import com.caseflow.email.api.dto.MailboxConnectionTestResponse;
import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.domain.InitialSyncStrategy;
import com.caseflow.email.repository.EmailMailboxRepository;
import com.caseflow.storage.ObjectStorageService;
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
    @Mock private ObjectStorageService objectStorageService;

    @InjectMocks
    private ImapMailboxPoller poller;

    private EmailMailbox mailbox;

    @BeforeEach
    void setup() {
        mailbox = new EmailMailbox();
        mailbox.setAddress("inbox@example.com");
        mailbox.setImapHost("imap.example.com");
        mailbox.setImapUsername("inbox@example.com");
        mailbox.setImapPassword("secret");
        mailbox.setImapFolder("INBOX");
        mailbox.setPollingEnabled(true);
        mailbox.setPollIntervalSeconds(60);
        mailbox.setInitialSyncStrategy(InitialSyncStrategy.START_FROM_LATEST);
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

    // ── Connection failure → lastPollError set, lease released ───────────────

    @Test
    void pollMailbox_setsLastPollError_onConnectionFailure() throws Exception {
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
    void pollMailbox_releasesLease_onConnectionFailure() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        mailbox.setImapHost("localhost");
        mailbox.setImapPort(port);
        mailbox.setPollLockedBy("instance-abc");
        mailbox.setPollLeasedUntil(Instant.now().plusSeconds(600));

        poller.pollMailbox(mailbox);

        // Lock must always be cleared regardless of outcome
        assertThat(mailbox.getPollLockedBy()).isNull();
        assertThat(mailbox.getPollLeasedUntil()).isNull();
        verify(mailboxRepository).save(mailbox);
    }

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

    @Test
    void pollMailbox_clearsLastPollError_guard_doesNotTouch() {
        // Guard exits (polling disabled) before touching the mailbox — error unchanged
        mailbox.setPollingEnabled(false);
        mailbox.setLastPollError("previous error");

        poller.pollMailbox(mailbox);

        verify(mailboxRepository, never()).save(any());
        assertThat(mailbox.getLastPollError()).isEqualTo("previous error");
    }

    // ── Initial sync strategy ─────────────────────────────────────────────────

    @Test
    void pollMailbox_withStartFromLatest_attemptsConnection_whenLastSeenUidIsNull() throws Exception {
        // With START_FROM_LATEST and null lastSeenUid, the poller should try to connect
        // (to read current max UID), fail, and record the error — NOT silently skip.
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        mailbox.setImapHost("localhost");
        mailbox.setImapPort(port);
        mailbox.setLastSeenUid(null);
        mailbox.setInitialSyncStrategy(InitialSyncStrategy.START_FROM_LATEST);

        poller.pollMailbox(mailbox);

        // Reached the connection attempt — not short-circuited by guard
        verify(mailboxRepository).save(mailbox);
        assertThat(mailbox.getLastPollError()).isNotNull();
        verify(ingressService, never()).receiveEvent(any());
    }

    @Test
    void pollMailbox_withBackfillAll_attemptsConnection_whenLastSeenUidIsNull() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        mailbox.setImapHost("localhost");
        mailbox.setImapPort(port);
        mailbox.setLastSeenUid(null);
        mailbox.setInitialSyncStrategy(InitialSyncStrategy.BACKFILL_ALL);

        poller.pollMailbox(mailbox);

        verify(mailboxRepository).save(mailbox);
        assertThat(mailbox.getLastPollError()).isNotNull();
        verify(ingressService, never()).receiveEvent(any());
    }

    @Test
    void pollMailbox_defaultsToStartFromLatest_whenStrategyIsNull() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        mailbox.setImapHost("localhost");
        mailbox.setImapPort(port);
        mailbox.setLastSeenUid(null);
        mailbox.setInitialSyncStrategy(null);  // null → defaults to START_FROM_LATEST

        poller.pollMailbox(mailbox);

        // Connection attempted, error recorded
        verify(mailboxRepository).save(mailbox);
        assertThat(mailbox.getLastPollError()).isNotNull();
    }

    // ── Connection test ───────────────────────────────────────────────────────

    @Test
    void testImapConnection_returnsFailure_whenConnectionRefused() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        mailbox.setImapHost("localhost");
        mailbox.setImapPort(port);
        mailbox.setImapUseSsl(false);

        MailboxConnectionTestResponse result = poller.testImapConnection(mailbox);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isNotBlank();
        assertThat(result.testedAt()).isNotNull();
    }

    @Test
    void testImapConnection_doesNotExposePlaintextPassword_inErrorMessage() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        mailbox.setImapHost("localhost");
        mailbox.setImapPort(port);
        // Even if the IMAP library echoes credentials in the error, they must be stripped
        mailbox.setImapPassword("super-secret-password");

        MailboxConnectionTestResponse result = poller.testImapConnection(mailbox);

        assertThat(result.message()).doesNotContain("super-secret-password");
    }

    @Test
    void testImapConnection_doesNotSaveMailbox() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        mailbox.setImapHost("localhost");
        mailbox.setImapPort(port);

        poller.testImapConnection(mailbox);

        // Connection test must be read-only — no DB side effects
        verify(mailboxRepository, never()).save(any());
    }
}
