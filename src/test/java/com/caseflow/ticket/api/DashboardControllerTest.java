package com.caseflow.ticket.api;

import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.ticket.api.dto.DashboardStatsResponse;
import com.caseflow.ticket.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import(SecurityConfig.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenService jwtTokenService;

    @MockBean
    private CaseFlowUserDetailsService userDetailsService;

    @MockBean
    private DashboardService dashboardService;

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void getStats_returns200_withStatsPayload() throws Exception {
        DashboardStatsResponse stats = new DashboardStatsResponse(
                10L, 7L, 2L, 1L, 3L, 2L, 5L, List.of());

        when(dashboardService.getStats(any())).thenReturn(stats);

        mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTickets").value(10))
                .andExpect(jsonPath("$.activeTickets").value(7))
                .andExpect(jsonPath("$.resolvedTickets").value(2))
                .andExpect(jsonPath("$.closedTickets").value(1))
                .andExpect(jsonPath("$.unassignedTickets").value(3))
                .andExpect(jsonPath("$.waitingOver24h").value(2))
                .andExpect(jsonPath("$.myActionRequired").value(5));
    }

    @Test
    void getStats_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_STATUS_CHANGE") // not TICKET_READ
    void getStats_returns403_withoutTicketReadPermission() throws Exception {
        mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isForbidden());
    }
}
