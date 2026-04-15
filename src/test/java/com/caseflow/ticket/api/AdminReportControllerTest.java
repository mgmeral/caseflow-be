package com.caseflow.ticket.api;

import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.exception.InvalidDateRangeException;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.ticket.api.dto.AdminCustomerReportRow;
import com.caseflow.ticket.api.dto.AdminReportSummaryResponse;
import com.caseflow.ticket.api.dto.AgingBucketsResponse;
import com.caseflow.ticket.api.dto.CustomerHealthSummary;
import com.caseflow.ticket.api.dto.TrendDataPoint;
import com.caseflow.ticket.api.dto.WorkloadSummaryResponse;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
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
    @WithMockUser(authorities = "PERM_REPORT_VIEW")
    void aggregateReport_returns400_whenFromAfterTo() throws Exception {
        when(reportingService.adminAggregateReport(any(), any(), any()))
                .thenThrow(new InvalidDateRangeException("dateFrom must not be after dateTo"));

        mockMvc.perform(get("/api/admin/reports/customers/tickets")
                        .param("from", "2026-12-01T00:00:00Z")
                        .param("to", "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
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

    // ── /api/admin/reports/summary ────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_REPORT_VIEW")
    void executiveSummary_returns200_withKpiBlock() throws Exception {
        AdminReportSummaryResponse summary = new AdminReportSummaryResponse(
                null, null,
                50L, 30L, 12L, 8L, 2L, 5L,
                3L, 45L, 120L,
                0.04, 0.10, 0.17, 0.40
        );
        when(reportingService.executiveSummary(isNull(), any(), any())).thenReturn(summary);

        mockMvc.perform(get("/api/admin/reports/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTickets").value(50))
                .andExpect(jsonPath("$.activeTickets").value(30))
                .andExpect(jsonPath("$.breachedTickets").value(3))
                .andExpect(jsonPath("$.avgFirstResponseMinutes").value(45))
                .andExpect(jsonPath("$.avgResolutionMinutes").value(120))
                .andExpect(jsonPath("$.reopenRate").value(0.04));
    }

    @Test
    @WithMockUser(authorities = "PERM_REPORT_VIEW")
    void executiveSummary_returns200_withCustomerFilter() throws Exception {
        AdminReportSummaryResponse summary = new AdminReportSummaryResponse(
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-31T23:59:59Z"),
                10L, 6L, 2L, 2L, 0L, 1L,
                1L, 30L, 90L,
                null, null, 0.17, 0.40
        );
        when(reportingService.executiveSummary(any(), any(), any())).thenReturn(summary);

        mockMvc.perform(get("/api/admin/reports/summary")
                        .param("customerId", "1")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-01-31T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTickets").value(10));
    }

    @Test
    @WithMockUser
    void executiveSummary_returns403_withoutPermission() throws Exception {
        mockMvc.perform(get("/api/admin/reports/summary"))
                .andExpect(status().isForbidden());
    }

    // ── /api/admin/reports/trend ──────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_REPORT_VIEW")
    void dailyTrend_returns200_withDataPoints() throws Exception {
        List<TrendDataPoint> points = List.of(
                new TrendDataPoint("2026-04-01", 5L, 2L, 1L, 0L),
                new TrendDataPoint("2026-04-02", 3L, 4L, 2L, 1L)
        );
        when(reportingService.dailyTrend(any(), any(), any())).thenReturn(points);

        mockMvc.perform(get("/api/admin/reports/trend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-04-01"))
                .andExpect(jsonPath("$[0].created").value(5))
                .andExpect(jsonPath("$[0].resolved").value(2))
                .andExpect(jsonPath("$[0].closed").value(1))
                .andExpect(jsonPath("$[0].breached").value(0))
                .andExpect(jsonPath("$[1].date").value("2026-04-02"))
                .andExpect(jsonPath("$[1].breached").value(1));
    }

    @Test
    @WithMockUser(authorities = "PERM_REPORT_VIEW")
    void dailyTrend_returns200_withEmptyList_whenNoActivity() throws Exception {
        when(reportingService.dailyTrend(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/reports/trend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser
    void dailyTrend_returns403_withoutPermission() throws Exception {
        mockMvc.perform(get("/api/admin/reports/trend"))
                .andExpect(status().isForbidden());
    }

    // ── /api/admin/reports/aging ──────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_REPORT_VIEW")
    void agingBuckets_returns200_withDistribution() throws Exception {
        AgingBucketsResponse aging = new AgingBucketsResponse(5L, 8L, 12L, 4L, 2L, 31L);
        when(reportingService.agingBuckets(any())).thenReturn(aging);

        mockMvc.perform(get("/api/admin/reports/aging"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.under4h").value(5))
                .andExpect(jsonPath("$.h4to24").value(8))
                .andExpect(jsonPath("$.d1to3").value(12))
                .andExpect(jsonPath("$.d3to7").value(4))
                .andExpect(jsonPath("$.over7d").value(2))
                .andExpect(jsonPath("$.total").value(31));
    }

    @Test
    @WithMockUser(authorities = "PERM_REPORT_VIEW")
    void agingBuckets_returns200_withCustomerFilter() throws Exception {
        AgingBucketsResponse aging = new AgingBucketsResponse(1L, 2L, 0L, 0L, 0L, 3L);
        when(reportingService.agingBuckets(any())).thenReturn(aging);

        mockMvc.perform(get("/api/admin/reports/aging").param("customerId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    @WithMockUser
    void agingBuckets_returns403_withoutPermission() throws Exception {
        mockMvc.perform(get("/api/admin/reports/aging"))
                .andExpect(status().isForbidden());
    }

    // ── /api/admin/reports/workload ───────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_REPORT_VIEW")
    void workloadSummary_returns200_withAgentAndGroupBreakdown() throws Exception {
        WorkloadSummaryResponse.AssigneeWorkload agent =
                new WorkloadSummaryResponse.AssigneeWorkload(1L, "alice", 8L, 2L, 1L);
        WorkloadSummaryResponse.GroupWorkload group =
                new WorkloadSummaryResponse.GroupWorkload(10L, "Support", 15L, 3L, 2L);
        WorkloadSummaryResponse workload = new WorkloadSummaryResponse(
                List.of(agent), List.of(group));
        when(reportingService.workloadSummary()).thenReturn(workload);

        mockMvc.perform(get("/api/admin/reports/workload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byAssignee[0].userId").value(1))
                .andExpect(jsonPath("$.byAssignee[0].username").value("alice"))
                .andExpect(jsonPath("$.byAssignee[0].activeCount").value(8))
                .andExpect(jsonPath("$.byAssignee[0].waitingCustomerCount").value(2))
                .andExpect(jsonPath("$.byAssignee[0].breachedCount").value(1))
                .andExpect(jsonPath("$.byGroup[0].groupId").value(10))
                .andExpect(jsonPath("$.byGroup[0].groupName").value("Support"))
                .andExpect(jsonPath("$.byGroup[0].activeCount").value(15))
                .andExpect(jsonPath("$.byGroup[0].unassignedCount").value(3))
                .andExpect(jsonPath("$.byGroup[0].breachedCount").value(2));
    }

    @Test
    @WithMockUser
    void workloadSummary_returns403_withoutPermission() throws Exception {
        mockMvc.perform(get("/api/admin/reports/workload"))
                .andExpect(status().isForbidden());
    }

    // ── /api/admin/reports/health ─────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_REPORT_VIEW")
    void customerHealth_returns200_withHealthScores() throws Exception {
        List<CustomerHealthSummary> health = List.of(
                new CustomerHealthSummary(1L, "Acme Corp", "#FF0000",
                        25L, 3L, 55L, 120L, CustomerHealthSummary.HealthScore.AT_RISK),
                new CustomerHealthSummary(2L, "Beta Inc", "#00FF00",
                        5L, 0L, 20L, 90L, CustomerHealthSummary.HealthScore.STABLE)
        );
        when(reportingService.customerHealthSummaries()).thenReturn(health);

        mockMvc.perform(get("/api/admin/reports/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerId").value(1))
                .andExpect(jsonPath("$[0].customerName").value("Acme Corp"))
                .andExpect(jsonPath("$[0].openTickets").value(25))
                .andExpect(jsonPath("$[0].breachedSlaCount").value(3))
                .andExpect(jsonPath("$[0].healthScore").value("AT_RISK"))
                .andExpect(jsonPath("$[1].healthScore").value("STABLE"));
    }

    @Test
    @WithMockUser(authorities = "PERM_REPORT_VIEW")
    void customerHealth_returns200_withEmptyList_whenNoCustomers() throws Exception {
        when(reportingService.customerHealthSummaries()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/reports/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser
    void customerHealth_returns403_withoutPermission() throws Exception {
        mockMvc.perform(get("/api/admin/reports/health"))
                .andExpect(status().isForbidden());
    }
}
