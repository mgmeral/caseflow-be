package com.caseflow.workflow.assignment.api;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.ticket.security.TicketAuthorizationService;
import com.caseflow.workflow.assignment.AssignmentService;
import com.caseflow.workflow.assignment.dto.AssignTicketRequest;
import com.caseflow.workflow.assignment.dto.AssignmentResponse;
import com.caseflow.workflow.assignment.dto.ReassignTicketRequest;
import com.caseflow.workflow.assignment.dto.UnassignTicketRequest;
import com.caseflow.workflow.assignment.mapper.AssignmentMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import com.caseflow.workflow.domain.Assignment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AssignmentController.class)
@Import(SecurityConfig.class)
class AssignmentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private JwtTokenService jwtTokenService;
    @MockBean private CaseFlowUserDetailsService userDetailsService;
    @MockBean private AssignmentService assignmentService;
    @MockBean private AssignmentMapper assignmentMapper;
    @MockBean(name = "ticketAuth") private TicketAuthorizationService ticketAuth;

    // ── POST /api/assignments/assign ──────────────────────────────────────────

    @Test
    void assign_returns200_withAssignmentResponse() throws Exception {
        CaseFlowUserDetails principal = mockPrincipal(1L);
        when(ticketAuth.canAssignTicket(any(Authentication.class), anyLong(), anyLong(), isNull()))
                .thenReturn(true);

        AssignmentResponse response = makeResponse(10L, 5L, 2L, null, 1L);
        when(assignmentService.assign(anyLong(), anyLong(), isNull(), anyLong()))
                .thenReturn(new Assignment());
        when(assignmentMapper.toResponse(any())).thenReturn(response);

        AssignTicketRequest request = new AssignTicketRequest(5L, 2L, null);

        mockMvc.perform(post("/api/assignments/assign")
                        .with(csrf())
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.ticketId").value(5))
                .andExpect(jsonPath("$.assignedUserId").value(2))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void assign_returns403_whenNotAuthorized() throws Exception {
        CaseFlowUserDetails principal = mockPrincipal(1L);
        when(ticketAuth.canAssignTicket(any(), anyLong(), any(), any())).thenReturn(false);

        AssignTicketRequest request = new AssignTicketRequest(5L, 2L, null);

        mockMvc.perform(post("/api/assignments/assign")
                        .with(csrf())
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void assign_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/assignments/assign").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/assignments/reassign ────────────────────────────────────────

    @Test
    void reassign_returns200() throws Exception {
        CaseFlowUserDetails principal = mockPrincipal(1L);
        when(ticketAuth.canAssignTicket(any(Authentication.class), anyLong(), anyLong(), isNull()))
                .thenReturn(true);

        AssignmentResponse response = makeResponse(10L, 5L, 3L, null, 1L);
        when(assignmentService.reassign(anyLong(), anyLong(), isNull(), anyLong())).thenReturn(new Assignment());
        when(assignmentMapper.toResponse(any())).thenReturn(response);

        ReassignTicketRequest request = new ReassignTicketRequest(5L, 3L, null);

        mockMvc.perform(post("/api/assignments/reassign")
                        .with(csrf())
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedUserId").value(3));
    }

    // ── POST /api/assignments/unassign ────────────────────────────────────────

    @Test
    void unassign_returns204() throws Exception {
        CaseFlowUserDetails principal = mockPrincipal(1L);
        when(ticketAuth.canAssignTicket(any(Authentication.class), anyLong(), isNull(), isNull()))
                .thenReturn(true);

        UnassignTicketRequest request = new UnassignTicketRequest(5L);

        mockMvc.perform(post("/api/assignments/unassign")
                        .with(csrf())
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    // ── GET /api/assignments/by-ticket/{ticketId} ─────────────────────────────

    @Test
    void getActiveAssignment_returns200_whenFound() throws Exception {
        CaseFlowUserDetails principal = mockPrincipal(1L);
        when(ticketAuth.canReadTicket(any(Authentication.class), anyLong())).thenReturn(true);

        AssignmentResponse response = makeResponse(10L, 5L, 2L, null, 1L);
        when(assignmentService.getActiveAssignment(5L)).thenReturn(Optional.of(new Assignment()));
        when(assignmentMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(get("/api/assignments/by-ticket/5")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(5));
    }

    @Test
    void getActiveAssignment_returns404_whenNoneActive() throws Exception {
        CaseFlowUserDetails principal = mockPrincipal(1L);
        when(ticketAuth.canReadTicket(any(Authentication.class), anyLong())).thenReturn(true);
        when(assignmentService.getActiveAssignment(5L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/assignments/by-ticket/5")
                        .with(user(principal)))
                .andExpect(status().isNotFound());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AssignmentResponse makeResponse(Long id, Long ticketId, Long userId, Long groupId, Long assignedBy) {
        return new AssignmentResponse(id, ticketId, userId, groupId, assignedBy, Instant.now(), null, true);
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
        when(principal.getAuthorities()).thenReturn(List.of());
        return principal;
    }
}
