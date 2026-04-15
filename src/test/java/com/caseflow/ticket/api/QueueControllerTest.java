package com.caseflow.ticket.api;

import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.ticket.api.dto.QueueStatsResponse;
import com.caseflow.ticket.security.TicketAuthorizationService;
import com.caseflow.ticket.service.QueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QueueController.class)
@Import(SecurityConfig.class)
class QueueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private QueueService queueService;
    @MockBean private JwtTokenService jwtTokenService;
    @MockBean private CaseFlowUserDetailsService userDetailsService;
    @MockBean(name = "ticketAuth") private TicketAuthorizationService ticketAuth;

    @BeforeEach
    void permitAll() {
        when(ticketAuth.canViewAdminPool(any(Authentication.class))).thenReturn(true);
    }

    // ── /api/queue/stats ──────────────────────────────────────────────────────

    @Test
    @WithMockUser
    void getStats_returns200_withAllChipCounts() throws Exception {
        QueueStatsResponse stats = new QueueStatsResponse(15L, 5L, 3L, 2L);
        when(queueService.getStats(any())).thenReturn(stats);

        mockMvc.perform(get("/api/queue/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allUnassigned").value(15))
                .andExpect(jsonPath("$.highOrCritical").value(5))
                .andExpect(jsonPath("$.waitingOver8h").value(3))
                .andExpect(jsonPath("$.slaBreached").value(2));
    }

    @Test
    @WithMockUser
    void getStats_returns200_withZeroCounts_whenQueueEmpty() throws Exception {
        when(queueService.getStats(any())).thenReturn(new QueueStatsResponse(0L, 0L, 0L, 0L));

        mockMvc.perform(get("/api/queue/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allUnassigned").value(0))
                .andExpect(jsonPath("$.slaBreached").value(0));
    }

    @Test
    void getStats_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/queue/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getStats_returns403_whenCanViewAdminPoolDenied() throws Exception {
        when(ticketAuth.canViewAdminPool(any(Authentication.class))).thenReturn(false);

        mockMvc.perform(get("/api/queue/stats"))
                .andExpect(status().isForbidden());
    }

    // ── /api/queue ────────────────────────────────────────────────────────────

    @Test
    @WithMockUser
    void getQueue_returns200_withPagedTickets() throws Exception {
        when(queueService.getQueue(isNull(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @WithMockUser
    void getQueue_returns200_withPriorityFilter() throws Exception {
        when(queueService.getQueue(any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/queue").param("priority", "HIGH"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void getQueue_returns200_withPaginationParams() throws Exception {
        when(queueService.getQueue(any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/queue")
                        .param("page", "1")
                        .param("size", "10")
                        .param("sort", "createdAt")
                        .param("direction", "desc"))
                .andExpect(status().isOk());
    }

    @Test
    void getQueue_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/queue"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getQueue_returns403_whenCanViewAdminPoolDenied() throws Exception {
        when(ticketAuth.canViewAdminPool(any(Authentication.class))).thenReturn(false);

        mockMvc.perform(get("/api/queue"))
                .andExpect(status().isForbidden());
    }
}
