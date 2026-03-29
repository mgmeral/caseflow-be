package com.caseflow.email.api;

import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.email.api.dto.IngressEventResponse;
import com.caseflow.email.api.dto.QuarantineRequest;
import com.caseflow.email.api.mapper.IngressEventMapper;
import com.caseflow.email.domain.IngressEventStatus;
import com.caseflow.email.service.EmailIngressEventQueryService;
import com.caseflow.email.service.EmailIngressService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IngressEventController.class)
@Import(SecurityConfig.class)
class IngressEventControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private JwtTokenService jwtTokenService;
    @MockBean private CaseFlowUserDetailsService userDetailsService;
    @MockBean private EmailIngressEventQueryService queryService;
    @MockBean private EmailIngressService ingressService;
    @MockBean private IngressEventMapper mapper;
    @MockBean(name = "ticketAuth") private TicketAuthorizationService ticketAuth;

    // ── GET /api/admin/ingress-events/{id} ────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_OPERATIONS_VIEW")
    void getById_returns200() throws Exception {
        IngressEventResponse response = makeResponse(1L, IngressEventStatus.PROCESSED);
        when(queryService.getById(1L)).thenReturn(null);
        when(mapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(get("/api/admin/ingress-events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getById_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/admin/ingress-events/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void getById_returns403_whenMissingPermission() throws Exception {
        mockMvc.perform(get("/api/admin/ingress-events/1"))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/admin/ingress-events ──────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_OPERATIONS_VIEW")
    void list_returns200_allEvents() throws Exception {
        List<IngressEventResponse> responses = List.of(
                makeResponse(1L, IngressEventStatus.PROCESSED),
                makeResponse(2L, IngressEventStatus.FAILED)
        );
        when(queryService.findAll()).thenReturn(List.of());
        when(mapper.toResponseList(any())).thenReturn(responses);

        mockMvc.perform(get("/api/admin/ingress-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_OPERATIONS_VIEW")
    void list_returns200_filteredByStatus() throws Exception {
        when(queryService.findByStatus(IngressEventStatus.FAILED)).thenReturn(List.of());
        when(mapper.toResponseList(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/ingress-events").param("status", "FAILED"))
                .andExpect(status().isOk());

        verify(queryService).findByStatus(IngressEventStatus.FAILED);
    }

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_OPERATIONS_VIEW")
    void list_returns200_filteredByMailboxId() throws Exception {
        when(queryService.findByMailboxId(5L)).thenReturn(List.of());
        when(mapper.toResponseList(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/ingress-events").param("mailboxId", "5"))
                .andExpect(status().isOk());

        verify(queryService).findByMailboxId(5L);
    }

    // ── POST /api/admin/ingress-events/{id}/process ───────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_OPERATIONS_MANAGE")
    void replay_returns200() throws Exception {
        IngressEventResponse response = makeResponse(1L, IngressEventStatus.PROCESSED);
        when(queryService.getById(1L)).thenReturn(null);
        when(mapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(post("/api/admin/ingress-events/1/process").with(csrf()))
                .andExpect(status().isOk());

        verify(ingressService).processEvent(1L);
    }

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_OPERATIONS_VIEW")
    void replay_returns403_whenMissingManagePermission() throws Exception {
        mockMvc.perform(post("/api/admin/ingress-events/1/process").with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/admin/ingress-events/{id}/quarantine ────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_OPERATIONS_MANAGE")
    void quarantine_returns200_withReason() throws Exception {
        QuarantineRequest request = new QuarantineRequest("Spam detected");
        IngressEventResponse response = makeResponse(1L, IngressEventStatus.QUARANTINED);
        when(queryService.getById(1L)).thenReturn(null);
        when(mapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(post("/api/admin/ingress-events/1/quarantine")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUARANTINED"));

        verify(ingressService).quarantineEvent(1L, "Spam detected");
    }

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_OPERATIONS_MANAGE")
    void quarantine_returns400_whenReasonBlank() throws Exception {
        QuarantineRequest request = new QuarantineRequest("");

        mockMvc.perform(post("/api/admin/ingress-events/1/quarantine")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/admin/ingress-events/{id}/release ───────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_OPERATIONS_MANAGE")
    void release_returns200() throws Exception {
        IngressEventResponse response = makeResponse(1L, IngressEventStatus.RECEIVED);
        when(queryService.getById(1L)).thenReturn(null);
        when(mapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(post("/api/admin/ingress-events/1/release").with(csrf()))
                .andExpect(status().isOk());

        verify(ingressService).releaseEvent(1L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private IngressEventResponse makeResponse(Long id, IngressEventStatus status) {
        return new IngressEventResponse(
                id, null, "<msg-" + id + "@test.com>", "from@test.com",
                "Hello", Instant.now(), status, null, 0, null, null, null, null);
    }
}
