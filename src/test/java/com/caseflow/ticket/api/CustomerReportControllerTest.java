package com.caseflow.ticket.api;

import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.ticket.api.dto.CustomerTicketReportResponse;
import com.caseflow.ticket.security.TicketAuthorizationService;
import com.caseflow.ticket.service.ReportingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerReportController.class)
@Import(SecurityConfig.class)
class CustomerReportControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ReportingService reportingService;
    @MockBean private JwtTokenService jwtTokenService;
    @MockBean private CaseFlowUserDetailsService userDetailsService;
    @MockBean(name = "ticketAuth") private TicketAuthorizationService ticketAuth;

    @Test
    @WithMockUser(authorities = "PERM_REPORT_VIEW")
    void ticketReport_returns200_withStatusBuckets() throws Exception {
        CustomerTicketReportResponse report = new CustomerTicketReportResponse(
                1L, "Acme Corp",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-31T23:59:59Z"),
                10L, 6L, 2L, 3L, 1L, 2L, 2L, 0L,
                List.of(new CustomerTicketReportResponse.TagCount(1L, "BUG", "Bug", null, 3L))
        );
        when(reportingService.customerReport(anyLong(), any(), any())).thenReturn(report);

        mockMvc.perform(get("/api/customers/1/reports/tickets")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-01-31T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(1))
                .andExpect(jsonPath("$.customerName").value("Acme Corp"))
                .andExpect(jsonPath("$.totalCount").value(10))
                .andExpect(jsonPath("$.openCount").value(6))
                .andExpect(jsonPath("$.newCount").value(2))
                .andExpect(jsonPath("$.closedCount").value(2))
                .andExpect(jsonPath("$.byTag[0].tagCode").value("BUG"))
                .andExpect(jsonPath("$.byTag[0].count").value(3));
    }

    @Test
    @WithMockUser
    void ticketReport_returns403_withoutPermission() throws Exception {
        mockMvc.perform(get("/api/customers/1/reports/tickets"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "PERM_REPORT_VIEW")
    void ticketReport_works_withoutDateRange() throws Exception {
        CustomerTicketReportResponse report = new CustomerTicketReportResponse(
                1L, "Acme", null, null, 5L, 3L, 1L, 2L, 0L, 1L, 1L, 0L, List.of());
        when(reportingService.customerReport(anyLong(), any(), any())).thenReturn(report);

        mockMvc.perform(get("/api/customers/1/reports/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(5));
    }

    @Test
    @WithMockUser(authorities = "PERM_REPORT_VIEW")
    void ticketReport_works_withFromOnly() throws Exception {
        CustomerTicketReportResponse report = new CustomerTicketReportResponse(
                1L, "Acme", Instant.parse("2026-02-01T00:00:00Z"), null,
                3L, 2L, 1L, 1L, 0L, 1L, 0L, 0L, List.of());
        when(reportingService.customerReport(anyLong(), any(), any())).thenReturn(report);

        mockMvc.perform(get("/api/customers/1/reports/tickets")
                        .param("from", "2026-02-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3));
    }

    @Test
    @WithMockUser(authorities = "PERM_REPORT_VIEW")
    void ticketReport_works_withToOnly() throws Exception {
        CustomerTicketReportResponse report = new CustomerTicketReportResponse(
                1L, "Acme", null, Instant.parse("2026-03-31T23:59:59Z"),
                7L, 4L, 2L, 2L, 0L, 2L, 1L, 0L, List.of());
        when(reportingService.customerReport(anyLong(), any(), any())).thenReturn(report);

        mockMvc.perform(get("/api/customers/1/reports/tickets")
                        .param("to", "2026-03-31T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(7));
    }

    @Test
    @WithMockUser(authorities = "PERM_REPORT_VIEW")
    void ticketReport_returns200_withZeroCounts_whenNoTickets() throws Exception {
        CustomerTicketReportResponse emptyReport = new CustomerTicketReportResponse(
                1L, "Empty Corp", null, null, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, List.of());
        when(reportingService.customerReport(anyLong(), any(), any())).thenReturn(emptyReport);

        mockMvc.perform(get("/api/customers/1/reports/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.openCount").value(0))
                .andExpect(jsonPath("$.newCount").value(0))
                .andExpect(jsonPath("$.closedCount").value(0))
                .andExpect(jsonPath("$.byTag").isEmpty());
    }
}
