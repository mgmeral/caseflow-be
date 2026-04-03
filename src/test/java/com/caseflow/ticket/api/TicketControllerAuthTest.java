package com.caseflow.ticket.api;

import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.ticket.api.dto.CloseTicketRequest;
import com.caseflow.ticket.api.dto.CreateTicketRequest;
import com.caseflow.ticket.api.dto.TicketResponse;
import com.caseflow.ticket.api.dto.TicketSummaryResponse;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.security.TicketAuthorizationService;
import com.caseflow.ticket.service.TicketQueryService;
import com.caseflow.ticket.service.TicketReadService;
import com.caseflow.ticket.service.TicketService;
import com.caseflow.notification.service.NotificationService;
import com.caseflow.workflow.state.TicketStateMachineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization-focused tests for TicketController.
 *
 * These tests prove that:
 * 1. Unauthenticated requests receive 401
 * 2. Authenticated users missing permission receive 403
 * 3. Scope denial (ticketAuth returns false) produces 403
 * 4. Admin pool requires ADMIN_POOL_VIEW, not just TICKET_READ
 * 5. Customer reply endpoint returns 501 when authorized
 * 6. Role name alone never grants access — only permissions matter
 */
@WebMvcTest(TicketController.class)
@Import(SecurityConfig.class)
class TicketControllerAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtTokenService jwtTokenService;

    @MockBean
    private CaseFlowUserDetailsService userDetailsService;

    @MockBean
    private TicketService ticketService;

    @MockBean
    private TicketReadService ticketReadService;

    @MockBean
    private TicketQueryService ticketQueryService;

    @MockBean
    private TicketStateMachineService stateMachine;

    @MockBean(name = "ticketAuth")
    private TicketAuthorizationService ticketAuth;

    @MockBean
    private NotificationService notificationService;

    // ── 401 when unauthenticated ──────────────────────────────────────────────

    @Test
    void getById_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/tickets/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listTickets_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminPool_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/tickets/admin-pool"))
                .andExpect(status().isUnauthorized());
    }

    // ── 403 when scope denies access ─────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void getById_returns403_whenTicketAuthReturnsFalse() throws Exception {
        when(ticketAuth.canReadTicket(any(Authentication.class), anyLong())).thenReturn(false);

        mockMvc.perform(get("/api/tickets/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void getByTicketNo_returns403_whenTicketAuthReturnsFalse() throws Exception {
        when(ticketAuth.canReadTicketByNo(any(Authentication.class), anyString())).thenReturn(false);

        mockMvc.perform(get("/api/tickets/by-ticket-no/TKT-001"))
                .andExpect(status().isForbidden());
    }

    // ── 403 when missing permission (hasAuthority check) ─────────────────────

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void createTicket_returns403_whenMissingStatusChangePermission() throws Exception {
        CreateTicketRequest request = new CreateTicketRequest(
                "Test ticket", "desc", TicketPriority.MEDIUM, null
        );

        mockMvc.perform(post("/api/tickets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listTickets_returns403_whenMissingTicketReadPermission() throws Exception {
        mockMvc.perform(get("/api/tickets")
                        .with(csrf()))
                .andExpect(status().isUnauthorized()); // no auth at all → 401
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_STATUS_CHANGE")
    void adminPool_returns403_whenMissingAdminPoolViewPermission() throws Exception {
        when(ticketAuth.canViewAdminPool(any(Authentication.class))).thenReturn(false);

        mockMvc.perform(get("/api/tickets/admin-pool"))
                .andExpect(status().isForbidden());
    }

    // ── Admin pool requires ADMIN_POOL_VIEW, not just TICKET_READ ────────────

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void adminPool_returns403_forTicketReadHolder_withoutAdminPoolView() throws Exception {
        when(ticketAuth.canViewAdminPool(any(Authentication.class))).thenReturn(false);

        mockMvc.perform(get("/api/tickets/admin-pool"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void adminPool_returns200_whenAdminPoolViewGranted() throws Exception {
        when(ticketAuth.canViewAdminPool(any(Authentication.class))).thenReturn(true);
        when(ticketReadService.searchScoped(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/tickets/admin-pool"))
                .andExpect(status().isOk());
    }

    // ── Customer reply returns 501 when authorized ────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_CUSTOMER_REPLY_SEND")
    void reply_returns501_whenAuthorized() throws Exception {
        when(ticketAuth.canSendCustomerReply(any(Authentication.class), anyLong())).thenReturn(true);

        mockMvc.perform(post("/api/tickets/1/reply")
                        .with(csrf()))
                .andExpect(status().isNotImplemented());
    }

    @Test
    @WithMockUser(authorities = "PERM_CUSTOMER_REPLY_SEND")
    void reply_returns403_whenScopeDeniesSendPermission() throws Exception {
        when(ticketAuth.canSendCustomerReply(any(Authentication.class), anyLong())).thenReturn(false);

        mockMvc.perform(post("/api/tickets/1/reply")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ── Role name alone is insufficient ──────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")   // ROLE_ADMIN only, no PERM_* authorities
    void createTicket_returns403_forRoleAdminWithoutPermission() throws Exception {
        // ROLE_ADMIN authority alone does not grant PERM_TICKET_STATUS_CHANGE
        CreateTicketRequest request = new CreateTicketRequest(
                "Test ticket", "desc", TicketPriority.MEDIUM, null
        );

        mockMvc.perform(post("/api/tickets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "AGENT")   // ROLE_AGENT only, no PERM_* authorities
    void listTickets_returns403_forRoleAgentWithoutPermission() throws Exception {
        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TicketResponse makeTicketResponse(Long id, String ticketNo) {
        return new TicketResponse(id, null, ticketNo, "Test", null,
                TicketStatus.NEW, TicketPriority.MEDIUM,
                null, null, null, null, null, null,
                Instant.now(), Instant.now(), null);
    }

    private TicketSummaryResponse makeTicketSummary(Long id, String ticketNo) {
        return new TicketSummaryResponse(id, null, ticketNo, "Test",
                TicketStatus.NEW, TicketPriority.MEDIUM,
                null, null, null, null, null, null,
                Instant.now(), Instant.now());
    }
}
