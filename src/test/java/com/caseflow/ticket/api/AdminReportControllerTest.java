package com.caseflow.ticket.api;

import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.ticket.api.dto.AdminCustomerReportRow;
import com.caseflow.ticket.service.ReportingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminReportController.class)
@Import(SecurityConfig.class)
class AdminReportControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ReportingService reportingService;
    @MockBean private JwtTokenService jwtTokenService;
    @MockBean private CaseFlowUserDetailsService userDetailsService;

    @Test
    @WithMockUser(authorities = "PERM_REPORT_VIEW")
    void aggregateReport_returns200_withPagedRows() throws Exception {
        AdminCustomerReportRow row = new AdminCustomerReportRow(
                1L, "Acme Corp", null, 20L, 12L, 3L, 5L, 2L, 4L, 4L, 2L);

        when(reportingService.adminAggregateReport(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/admin/reports/customers/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].customerId").value(1))
                .andExpect(jsonPath("$.items[0].customerName").value("Acme Corp"))
                .andExpect(jsonPath("$.items[0].totalCount").value(20))
                .andExpect(jsonPath("$.items[0].openCount").value(12))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    @WithMockUser(authorities = "PERM_REPORT_VIEW")
    void aggregateReport_returns200_withDateRangeParams() throws Exception {
        when(reportingService.adminAggregateReport(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/admin/reports/customers/tickets")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-01-31T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @WithMockUser(authorities = "PERM_REPORT_VIEW")
    void aggregateReport_returns200_withPaginationParams() throws Exception {
        when(reportingService.adminAggregateReport(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 5), 0));

        mockMvc.perform(get("/api/admin/reports/customers/tickets")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    @WithMockUser
    void aggregateReport_returns403_withoutPermission() throws Exception {
        mockMvc.perform(get("/api/admin/reports/customers/tickets"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aggregateReport_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/admin/reports/customers/tickets"))
                .andExpect(status().isUnauthorized());
    }
}
