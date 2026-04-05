package com.caseflow.email.api;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.email.api.dto.ReplyPreviewRequest;
import com.caseflow.email.api.dto.ReplyPreviewResponse;
import com.caseflow.email.api.dto.UnifiedEmailDetailResponse;
import com.caseflow.email.service.ReplyPreviewService;
import com.caseflow.email.service.TicketEmailDetailService;
import com.caseflow.identity.service.UserService;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.security.TicketAuthorizationService;
import com.caseflow.ticket.service.TicketQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketEmailPublicController.class)
@Import(SecurityConfig.class)
class TicketEmailPublicControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private JwtTokenService jwtTokenService;
    @MockBean private CaseFlowUserDetailsService userDetailsService;
    @MockBean(name = "ticketAuth") private TicketAuthorizationService ticketAuth;
    @MockBean private TicketQueryService ticketQueryService;
    @MockBean private TicketEmailDetailService detailService;
    @MockBean private ReplyPreviewService previewService;
    @MockBean private UserService userService;

    private final UUID publicId = UUID.randomUUID();

    // ── GET /api/tickets/{publicId}/email/detail/{type}/{id} ──────────────────

    @Test
    @WithMockUser
    void getEmailDetail_returns200_forEmailDocument() throws Exception {
        when(ticketAuth.canViewTicketEmailByPublicId(any(), any())).thenReturn(true);
        when(ticketQueryService.getByPublicId(publicId)).thenReturn(ticket());

        UnifiedEmailDetailResponse detail = buildDetailResponse("EMAIL_DOCUMENT", "doc-abc-123");
        when(detailService.resolve(any(), anyString(), anyString())).thenReturn(detail);

        mockMvc.perform(get("/api/tickets/{id}/email/detail/EMAIL_DOCUMENT/doc-abc-123", publicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detailType").value("EMAIL_DOCUMENT"))
                .andExpect(jsonPath("$.id").value("doc-abc-123"))
                .andExpect(jsonPath("$.direction").value("INBOUND"));
    }

    @Test
    @WithMockUser
    void getEmailDetail_returns200_forOutboundDispatch() throws Exception {
        when(ticketAuth.canViewTicketEmailByPublicId(any(), any())).thenReturn(true);
        when(ticketQueryService.getByPublicId(publicId)).thenReturn(ticket());

        UnifiedEmailDetailResponse detail = buildDetailResponse("OUTBOUND_DISPATCH", "42");
        when(detailService.resolve(any(), anyString(), anyString())).thenReturn(detail);

        mockMvc.perform(get("/api/tickets/{id}/email/detail/OUTBOUND_DISPATCH/42", publicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detailType").value("OUTBOUND_DISPATCH"))
                .andExpect(jsonPath("$.id").value("42"));
    }

    @Test
    @WithMockUser
    void getEmailDetail_returns403_whenNotInScope() throws Exception {
        when(ticketAuth.canViewTicketEmailByPublicId(any(), any())).thenReturn(false);

        mockMvc.perform(get("/api/tickets/{id}/email/detail/EMAIL_DOCUMENT/doc-abc", publicId))
                .andExpect(status().isForbidden());
    }

    @Test
    void getEmailDetail_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/tickets/{id}/email/detail/EMAIL_DOCUMENT/doc-abc", publicId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getEmailDetail_returns404_whenDocumentBelongsToDifferentTicket() throws Exception {
        when(ticketAuth.canViewTicketEmailByPublicId(any(), any())).thenReturn(true);
        when(ticketQueryService.getByPublicId(publicId)).thenReturn(ticket());
        when(detailService.resolve(any(), anyString(), anyString()))
                .thenThrow(new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Email document not found under this ticket"));

        mockMvc.perform(get("/api/tickets/{id}/email/detail/EMAIL_DOCUMENT/other-doc", publicId))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void getEmailDetail_returns400_forInvalidDetailType() throws Exception {
        when(ticketAuth.canViewTicketEmailByPublicId(any(), any())).thenReturn(true);
        when(ticketQueryService.getByPublicId(publicId)).thenReturn(ticket());
        when(detailService.resolve(any(), anyString(), anyString()))
                .thenThrow(new ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Unknown detailType: INVALID_TYPE"));

        mockMvc.perform(get("/api/tickets/{id}/email/detail/INVALID_TYPE/some-id", publicId))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/tickets/{publicId}/email/reply/preview ─────────────────────

    @Test
    void previewReply_returns200_withRenderedPreview() throws Exception {
        CaseFlowUserDetails principal = mockPrincipal(1L);
        when(ticketAuth.canSendTicketEmailReplyByPublicId(any(), any())).thenReturn(true);
        when(ticketQueryService.getByPublicId(publicId)).thenReturn(ticket());
        when(userService.getById(1L)).thenThrow(new RuntimeException("no user")); // fallback to null name
        when(previewService.preview(any(), any(), any())).thenReturn(buildPreviewResponse());

        ReplyPreviewRequest request = new ReplyPreviewRequest(
                10L, 100L, null, null, null, "Hello customer", null);

        mockMvc.perform(post("/api/tickets/{id}/email/reply/preview", publicId)
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.derivedToAddress").value("customer@example.com"))
                .andExpect(jsonPath("$.isEditable").value(true));
    }

    @Test
    void previewReply_returns400_withInactiveMailbox() throws Exception {
        CaseFlowUserDetails principal = mockPrincipal(1L);
        when(ticketAuth.canSendTicketEmailReplyByPublicId(any(), any())).thenReturn(true);
        when(ticketQueryService.getByPublicId(publicId)).thenReturn(ticket());
        when(previewService.preview(any(), any(), any()))
                .thenThrow(new ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Mailbox 99 is not active"));

        ReplyPreviewRequest request = new ReplyPreviewRequest(99L, null, null, null, null, "body", null);
        mockMvc.perform(post("/api/tickets/{id}/email/reply/preview", publicId)
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void previewReply_returns400_withSourceEventFromDifferentTicket() throws Exception {
        CaseFlowUserDetails principal = mockPrincipal(1L);
        when(ticketAuth.canSendTicketEmailReplyByPublicId(any(), any())).thenReturn(true);
        when(ticketQueryService.getByPublicId(publicId)).thenReturn(ticket());
        when(previewService.preview(any(), any(), any()))
                .thenThrow(new ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Source event 55 does not belong to this ticket"));

        ReplyPreviewRequest request = new ReplyPreviewRequest(10L, 55L, null, null, null, "body", null);
        mockMvc.perform(post("/api/tickets/{id}/email/reply/preview", publicId)
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void previewReply_returns403_whenNoReplyPermission() throws Exception {
        when(ticketAuth.canSendTicketEmailReplyByPublicId(any(), any())).thenReturn(false);

        ReplyPreviewRequest request = new ReplyPreviewRequest(10L, null, null, null, null, "body", null);
        mockMvc.perform(post("/api/tickets/{id}/email/reply/preview", publicId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Ticket ticket() {
        Ticket t = new Ticket();
        t.setTicketNo("TKT-001");
        t.setSubject("Test");
        t.setStatus(TicketStatus.IN_PROGRESS);
        t.setPriority(TicketPriority.MEDIUM);
        return t;
    }

    private UnifiedEmailDetailResponse buildDetailResponse(String detailType, String id) {
        return new UnifiedEmailDetailResponse(
                detailType, id, publicId,
                "EMAIL_DOCUMENT".equals(detailType) ? "INBOUND" : "OUTBOUND",
                1L, "Support", "support@example.com",
                "<msg@test>", null,
                "from@test.com", "to@test.com",
                null, null, null,
                "Test subject", "RECEIVED", null,
                null, Instant.now(), Instant.now(),
                "body text", null, "body text",
                List.of(), null, null, null
        );
    }

    private ReplyPreviewResponse buildPreviewResponse() {
        return new ReplyPreviewResponse(
                publicId, "EMAIL_DOCUMENT", "doc-123",
                10L, "Support", "support@example.com",
                "customer@example.com", "support@example.com",
                "Re: Test", "Hello customer\n\n--\nTicket: TKT-001", "<p>Hello customer</p>",
                null, List.of(), List.of(), true, Instant.now()
        );
    }

    private CaseFlowUserDetails mockPrincipal(Long userId) {
        CaseFlowUserDetails p = mock(CaseFlowUserDetails.class);
        when(p.getUserId()).thenReturn(userId);
        when(p.getUsername()).thenReturn("agent");
        when(p.getPassword()).thenReturn("");
        when(p.isEnabled()).thenReturn(true);
        when(p.isAccountNonExpired()).thenReturn(true);
        when(p.isAccountNonLocked()).thenReturn(true);
        when(p.isCredentialsNonExpired()).thenReturn(true);
        when(p.getAuthorities()).thenReturn(List.of());
        return p;
    }
}
