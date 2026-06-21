package com.caseflow.common.api;

import com.caseflow.auth.AccountLockedException;
import com.caseflow.common.exception.CustomerNotFoundException;
import com.caseflow.common.exception.DuplicateEmailException;
import com.caseflow.common.exception.EmailDispatchException;
import com.caseflow.common.exception.InvalidTicketStateException;
import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.ticket.domain.TicketStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest req;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        req = new MockHttpServletRequest("GET", "/api/test");
    }

    // ── 404 Not Found ──────────────────────────────────────────────────────────

    @Test
    void ticketNotFound_returns404_withDerivedCode() {
        var resp = handler.handleNotFound(new TicketNotFoundException(99L), req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().status()).isEqualTo(404);
        assertThat(resp.getBody().code()).isEqualTo("TICKET_NOT_FOUND");
    }

    @Test
    void customerNotFound_returns404_withDerivedCode() {
        var resp = handler.handleNotFound(new CustomerNotFoundException(5L), req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().code()).isEqualTo("CUSTOMER_NOT_FOUND");
    }

    // ── 422 Invalid State ──────────────────────────────────────────────────────

    @Test
    void invalidTicketState_returns422() {
        var resp = handler.handleInvalidState(
                new InvalidTicketStateException(TicketStatus.CLOSED, TicketStatus.IN_PROGRESS), req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().code()).isEqualTo("INVALID_TICKET_STATE");
        assertThat(resp.getBody().message()).contains("CLOSED");
    }

    // ── 409 Conflicts ─────────────────────────────────────────────────────────

    @Test
    void optimisticLock_returns409_concurrentModification() {
        var resp = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException("Ticket", 1L), req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().code()).isEqualTo("CONCURRENT_MODIFICATION");
    }

    @Test
    void dataIntegrity_returns409_hidingConstraintDetails() {
        var resp = handler.handleDataIntegrity(
                new DataIntegrityViolationException("unique constraint uk_tickets_ticket_no"), req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().code()).isEqualTo("DATA_INTEGRITY_VIOLATION");
        assertThat(resp.getBody().message()).doesNotContain("uk_tickets");
    }

    @Test
    void duplicateEmail_returns409() {
        var resp = handler.handleDuplicateEmail(new DuplicateEmailException("dup@example.com"), req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().code()).isEqualTo("DUPLICATE_EMAIL");
    }

    @Test
    void illegalState_returns409() {
        var resp = handler.handleIllegalState(new IllegalStateException("Already linked"), req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().code()).isEqualTo("CONFLICT");
    }

    // ── 400 Bad Request ───────────────────────────────────────────────────────

    @Test
    void illegalArgument_returns400() {
        var resp = handler.handleIllegalArgument(new IllegalArgumentException("bad input"), req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().code()).isEqualTo("BAD_REQUEST");
        assertThat(resp.getBody().message()).isEqualTo("bad input");
    }

    // ── 401 / 403 Security ────────────────────────────────────────────────────

    @Test
    void badCredentials_returns401_withGenericMessage() {
        var resp = handler.handleBadCredentials(new BadCredentialsException("Invalid credentials"), req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody().code()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(resp.getBody().message()).isEqualTo("Invalid credentials");
    }

    @Test
    void accessDenied_returns403() {
        var resp = handler.handleAccessDenied(new AccessDeniedException("forbidden"), req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().code()).isEqualTo("FORBIDDEN");
    }

    // ── 429 Account Locked ────────────────────────────────────────────────────

    @Test
    void accountLocked_returns429() {
        Instant until = Instant.now().plusSeconds(900);
        var resp = handler.handleAccountLocked(new AccountLockedException(until), req);

        assertThat(resp.getStatusCode().value()).isEqualTo(429);
        assertThat(resp.getBody().code()).isEqualTo("ACCOUNT_LOCKED");
    }

    // ── 413 Payload Too Large ─────────────────────────────────────────────────

    @Test
    void maxUploadSize_returns413() {
        var resp = handler.handleMaxUploadSize(new MaxUploadSizeExceededException(10 * 1024 * 1024L), req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(resp.getBody().code()).isEqualTo("FILE_TOO_LARGE");
    }

    // ── 503 Email Dispatch ────────────────────────────────────────────────────

    @Test
    void emailDispatch_returns503() {
        var resp = handler.handleEmailDispatch(new EmailDispatchException("SMTP timeout"), req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resp.getBody().code()).isEqualTo("EMAIL_DISPATCH_ERROR");
    }

    // ── 500 Unexpected ────────────────────────────────────────────────────────

    @Test
    void unexpectedException_returns500_withGenericMessage() {
        var resp = handler.handleUnexpected(new RuntimeException("NPE"), req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(resp.getBody().message()).isEqualTo("An unexpected error occurred");
    }

    // ── ErrorResponse contract ────────────────────────────────────────────────

    @Test
    void errorResponse_includesTimestampPathAndCorrelationId() {
        var resp = handler.handleNotFound(new TicketNotFoundException(1L), req);

        ErrorResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.timestamp()).isNotNull();
        assertThat(body.path()).isEqualTo("/api/test");
        assertThat(body.correlationId()).isNotBlank();
    }
}
