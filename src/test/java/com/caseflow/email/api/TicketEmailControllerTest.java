package com.caseflow.email.api;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.email.api.dto.DispatchResponse;
import com.caseflow.email.api.dto.EmailThreadItem;
import com.caseflow.email.api.dto.IngressEventResponse;
import com.caseflow.email.api.dto.SendReplyRequest;
import com.caseflow.email.api.mapper.DispatchMapper;
import com.caseflow.email.api.mapper.IngressEventMapper;
import com.caseflow.email.domain.DispatchStatus;
import com.caseflow.email.domain.IngressEventStatus;
import com.caseflow.email.service.EmailDispatchService;
import com.caseflow.email.service.EmailDocumentQueryService;
import com.caseflow.email.service.EmailIngressEventQueryService;
import com.caseflow.email.service.EmailMailboxService;
import com.caseflow.email.service.EmailReplyService;
import com.caseflow.ticket.security.TicketAuthorizationService;
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

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketEmailController.class)
@Import(SecurityConfig.class)
class TicketEmailControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private JwtTokenService jwtTokenService;
    @MockBean private CaseFlowUserDetailsService userDetailsService;
    @MockBean private EmailIngressEventQueryService ingressQueryService;
    @MockBean private EmailDispatchService dispatchService;
    @MockBean private EmailDocumentQueryService docQueryService;
    @MockBean private EmailReplyService replyService;
    @MockBean private EmailMailboxService mailboxService;
    @MockBean private IngressEventMapper ingressMapper;
    @MockBean private DispatchMapper dispatchMapper;
    @MockBean(name = "ticketAuth") private TicketAuthorizationService ticketAuth;

    // ── GET /api/tickets/{id}/email/thread ────────────────────────────────────

    @Test
    @WithMockUser
    void getThread_returns200_chronologicalList() throws Exception {
        when(ticketAuth.canViewTicketEmail(any(), anyLong())).thenReturn(true);

        Instant earlier = Instant.parse("2026-03-01T10:00:00Z");
        Instant later   = Instant.parse("2026-03-01T11:00:00Z");

        when(ingressQueryService.findByTicketId(1L)).thenReturn(List.of(
                ingressEvent(10L, earlier)
        ));
        when(dispatchService.findByTicketId(1L)).thenReturn(List.of(
                dispatch(20L, later)
        ));
        // documentId is null on in-memory event — bodyPreview lookup is skipped
        when(docQueryService.findById(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/tickets/1/email/thread"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].direction").value("INBOUND"))
                .andExpect(jsonPath("$[0].messageId").value("<msg-10@test.com>"))
                .andExpect(jsonPath("$[0].bodyPreview").doesNotExist())
                .andExpect(jsonPath("$[1].direction").value("OUTBOUND"))
                .andExpect(jsonPath("$[1].messageId").value("<dispatch-20@caseflow>"))
                .andExpect(jsonPath("$[1].bodyPreview").doesNotExist());
    }

    @Test
    void getThread_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/tickets/1/email/thread"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getThread_returns403_whenNoEmailViewPermission() throws Exception {
        when(ticketAuth.canViewTicketEmail(any(), anyLong())).thenReturn(false);

        mockMvc.perform(get("/api/tickets/1/email/thread"))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/tickets/{id}/email/inbound/{eventId} ─────────────────────────

    @Test
    @WithMockUser
    void getInboundEvent_returns200() throws Exception {
        when(ticketAuth.canViewTicketEmail(any(), anyLong())).thenReturn(true);
        IngressEventResponse response = ingressEventResponse(5L, IngressEventStatus.PROCESSED);
        when(ingressQueryService.getById(5L)).thenReturn(null);
        when(ingressMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(get("/api/tickets/1/email/inbound/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    // ── GET /api/tickets/{id}/email/outbound/{dispatchId} ─────────────────────

    @Test
    @WithMockUser
    void getOutboundDispatch_returns200() throws Exception {
        when(ticketAuth.canViewTicketEmail(any(), anyLong())).thenReturn(true);
        DispatchResponse response = dispatchResponse(7L, DispatchStatus.SENT);
        when(dispatchService.getById(7L)).thenReturn(null);
        when(dispatchMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(get("/api/tickets/1/email/outbound/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    // ── POST /api/tickets/{id}/email/reply ────────────────────────────────────

    @Test
    void sendReply_returns202_whenAuthorized() throws Exception {
        CaseFlowUserDetails principal = mockPrincipal(42L);
        when(ticketAuth.canSendTicketEmailReply(any(), anyLong())).thenReturn(true);
        when(mailboxService.getById(1L)).thenReturn(mailboxWithAddress("support@caseflow.dev"));

        SendReplyRequest request = new SendReplyRequest(
                1L, "customer@example.com", "Re: issue", "Thank you", null, null);

        mockMvc.perform(post("/api/tickets/1/email/reply")
                        .with(csrf())
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }

    @Test
    void sendReply_returns403_whenNoReplyPermission() throws Exception {
        CaseFlowUserDetails principal = mockPrincipal(42L);
        when(ticketAuth.canSendTicketEmailReply(any(), anyLong())).thenReturn(false);

        SendReplyRequest request = new SendReplyRequest(
                1L, "customer@example.com", "Re: issue", "Thank you", null, null);

        mockMvc.perform(post("/api/tickets/1/email/reply")
                        .with(csrf())
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private com.caseflow.email.domain.EmailIngressEvent ingressEvent(Long id, Instant receivedAt) {
        com.caseflow.email.domain.EmailIngressEvent e = new com.caseflow.email.domain.EmailIngressEvent();
        e.setMessageId("<msg-" + id + "@test.com>");
        e.setRawFrom("from@test.com");
        e.setRawSubject("Hello");
        e.setStatus(IngressEventStatus.PROCESSED);
        e.setProcessingAttempts(0);
        e.setReceivedAt(receivedAt);
        return e;
    }

    private com.caseflow.email.domain.OutboundEmailDispatch dispatch(Long id, Instant sentAt) {
        com.caseflow.email.domain.OutboundEmailDispatch d = new com.caseflow.email.domain.OutboundEmailDispatch();
        d.setMessageId("<dispatch-" + id + "@caseflow>");
        d.setFromAddress("support@caseflow.dev");
        d.setToAddress("customer@example.com");
        d.setSubject("Re: Hello");
        d.setStatus(DispatchStatus.SENT);
        d.setSentAt(sentAt);
        return d;
    }

    private IngressEventResponse ingressEventResponse(Long id, IngressEventStatus status) {
        return new IngressEventResponse(
                id, null, "<msg-" + id + "@test.com>", "from@test.com",
                "Hello", Instant.now(), status, null, 0, null, null, null, null);
    }

    private DispatchResponse dispatchResponse(Long id, DispatchStatus status) {
        return new DispatchResponse(
                id, 1L, "<dispatch-" + id + "@caseflow>",
                "support@caseflow.dev", "customer@example.com", "Re: Hello",
                status, 1, null, Instant.now(), null, Instant.now(), Instant.now());
    }

    private com.caseflow.email.domain.EmailMailbox mailboxWithAddress(String address) {
        com.caseflow.email.domain.EmailMailbox m = new com.caseflow.email.domain.EmailMailbox();
        m.setAddress(address);
        return m;
    }

    private CaseFlowUserDetails mockPrincipal(Long userId) {
        CaseFlowUserDetails principal = mock(CaseFlowUserDetails.class);
        when(principal.getUserId()).thenReturn(userId);
        when(principal.getUsername()).thenReturn("testuser");
        when(principal.getPassword()).thenReturn("");
        when(principal.isEnabled()).thenReturn(true);
        when(principal.isAccountNonExpired()).thenReturn(true);
        when(principal.isAccountNonLocked()).thenReturn(true);
        when(principal.isCredentialsNonExpired()).thenReturn(true);
        when(principal.getAuthorities()).thenReturn(java.util.List.of());
        return principal;
    }
}
