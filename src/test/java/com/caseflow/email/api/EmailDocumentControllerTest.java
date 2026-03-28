package com.caseflow.email.api;

import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.email.api.dto.EmailDocumentResponse;
import com.caseflow.email.api.dto.EmailDocumentSummaryResponse;
import com.caseflow.email.api.dto.EmailAttachmentResponse;
import com.caseflow.email.api.dto.IngressEventResponse;
import com.caseflow.email.api.dto.IngestEmailRequest;
import com.caseflow.email.api.mapper.EmailDocumentMapper;
import com.caseflow.email.api.mapper.IngressEventMapper;
import com.caseflow.email.domain.EmailIngressEvent;
import com.caseflow.email.domain.IngressEventStatus;
import com.caseflow.email.service.EmailDocumentQueryService;
import com.caseflow.email.service.EmailIngressService;
import com.caseflow.ticket.security.TicketAuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmailDocumentController.class)
@Import(SecurityConfig.class)
class EmailDocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtTokenService jwtTokenService;

    @MockBean
    private CaseFlowUserDetailsService userDetailsService;

    @MockBean
    private EmailDocumentQueryService emailDocumentQueryService;

    @MockBean
    private EmailDocumentMapper emailDocumentMapper;

    @MockBean
    private EmailIngressService emailIngressService;

    @MockBean
    private IngressEventMapper ingressEventMapper;

    @MockBean(name = "ticketAuth")
    private TicketAuthorizationService ticketAuth;

    @BeforeEach
    void allowAll() {
        when(ticketAuth.canReadTicket(any(Authentication.class), anyLong())).thenReturn(true);
    }

    // ── GET /api/emails/{id} ──────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void getById_returns200_withFullDetail() throws Exception {
        EmailDocumentResponse response = makeDetailResponse("doc-1", 42L, "Hello, world", "<p>Hello</p>");

        when(emailDocumentQueryService.findById("doc-1")).thenReturn(Optional.of(new com.caseflow.email.document.EmailDocument()));
        when(emailDocumentMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(get("/api/emails/doc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("doc-1"))
                .andExpect(jsonPath("$.ticketId").value(42))
                .andExpect(jsonPath("$.textBody").value("Hello, world"))
                .andExpect(jsonPath("$.htmlBody").value("<p>Hello</p>"))
                .andExpect(jsonPath("$.attachments").isArray())
                .andExpect(jsonPath("$.attachments[0].fileName").value("report.pdf"));
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void getById_returns404_whenNotFound() throws Exception {
        when(emailDocumentQueryService.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/emails/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getById_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/emails/doc-1"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/emails/by-ticket/{ticketId} ─────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void getByTicket_returns200_withSummaryList() throws Exception {
        EmailDocumentSummaryResponse summary = makeSummaryResponse("doc-1", "thread-A", 42L);

        when(emailDocumentQueryService.findByTicketId(42L)).thenReturn(List.of(new com.caseflow.email.document.EmailDocument()));
        when(emailDocumentMapper.toSummaryResponseList(any())).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/emails/by-ticket/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("doc-1"))
                .andExpect(jsonPath("$[0].threadKey").value("thread-A"))
                .andExpect(jsonPath("$[0].ticketId").value(42));
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void getByTicket_returns200_withEmptyList_whenNoEmails() throws Exception {
        when(emailDocumentQueryService.findByTicketId(99L)).thenReturn(List.of());
        when(emailDocumentMapper.toSummaryResponseList(List.of())).thenReturn(List.of());

        mockMvc.perform(get("/api/emails/by-ticket/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── GET /api/emails/by-thread/{threadKey} ─────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void getByThread_returns200_withSummaryList() throws Exception {
        EmailDocumentSummaryResponse summary = makeSummaryResponse("doc-2", "thread-B", 5L);

        when(emailDocumentQueryService.findByThreadKey("thread-B")).thenReturn(List.of(new com.caseflow.email.document.EmailDocument()));
        when(emailDocumentMapper.toSummaryResponseList(any())).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/emails/by-thread/thread-B"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].threadKey").value("thread-B"));
    }

    // ── POST /api/emails/ingest ───────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_TICKET_STATUS_CHANGE")
    void ingest_returns202_withIngressEvent() throws Exception {
        IngestEmailRequest request = new IngestEmailRequest(
                "<msg-001@test.com>", null, null,
                "Support request", "customer@test.com",
                List.of("support@caseflow.dev"), null,
                "Please help with my issue.", null,
                Instant.now(), null, null
        );
        EmailIngressEvent event = new EmailIngressEvent();
        IngressEventResponse response = new IngressEventResponse(
                1L, null, "<msg-001@test.com>", "customer@test.com", "Support request",
                Instant.now(), IngressEventStatus.RECEIVED, null, 0, null, null, null, null);

        when(emailIngressService.receiveEvent(anyString(), anyString(), any(), anyString(),
                isNull(), any(Instant.class))).thenReturn(event);
        when(ingressEventMapper.toResponse(event)).thenReturn(response);

        mockMvc.perform(post("/api/emails/ingest")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.messageId").value("<msg-001@test.com>"))
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_STATUS_CHANGE")
    void ingest_returns400_whenMessageIdIsBlank() throws Exception {
        IngestEmailRequest request = new IngestEmailRequest(
                "", null, null, "Subject", "from@test.com",
                null, null, "body", null, Instant.now(), null, null
        );

        mockMvc.perform(post("/api/emails/ingest")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EmailDocumentResponse makeDetailResponse(String id, Long ticketId,
                                                     String textBody, String htmlBody) {
        return new EmailDocumentResponse(
                id,
                "<" + id + "@test.com>",
                "thread-A",
                "Test Subject",
                "sender@test.com",
                List.of("support@caseflow.dev"),
                List.of(),
                Instant.now(),
                Instant.now(),
                ticketId,
                textBody,
                htmlBody,
                List.of(new EmailAttachmentResponse("report.pdf", "emails/report.pdf", "application/pdf", 4096L))
        );
    }

    private EmailDocumentSummaryResponse makeSummaryResponse(String id, String threadKey, Long ticketId) {
        return new EmailDocumentSummaryResponse(
                id,
                "<" + id + "@test.com>",
                threadKey,
                "Test Subject",
                "sender@test.com",
                Instant.now(),
                ticketId
        );
    }
}
