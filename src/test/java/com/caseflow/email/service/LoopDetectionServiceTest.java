package com.caseflow.email.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopDetectionServiceTest {

    private final LoopDetectionService service = new LoopDetectionService();

    @Test
    void isLoop_returnsTrue_forOutOfOfficeSubject() {
        assertTrue(service.isLoop("Out of office: On holiday", "agent@example.com", null));
    }

    @Test
    void isLoop_returnsTrue_forAutomaticReplySubject() {
        assertTrue(service.isLoop("Automatic reply: Your support request", "agent@example.com", null));
    }

    @Test
    void isLoop_returnsTrue_forDeliveryFailureSubject() {
        assertTrue(service.isLoop("Mail Delivery Failed", "postmaster@example.com", null));
    }

    @Test
    void isLoop_returnsTrue_forMailerDaemonAddress() {
        assertTrue(service.isLoop("Hello", "MAILER-DAEMON@example.com", null));
    }

    @Test
    void isLoop_returnsTrue_forAutoSubmittedHeader() {
        assertTrue(service.isLoop("Hello", "user@example.com", List.of("auto-submitted: auto-replied")));
    }

    @Test
    void isLoop_returnsFalse_forNormalEmail() {
        assertFalse(service.isLoop("Support request", "customer@example.com", null));
    }

    @Test
    void isLoop_returnsFalse_forNullSubject() {
        assertFalse(service.isLoop(null, "customer@example.com", null));
    }
}
