package com.caseflow.ticket.api;

import com.caseflow.ticket.api.dto.CustomerTicketReportResponse;
import com.caseflow.ticket.service.ReportingService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Ticket report for a specific customer.
 *
 * <p>Requires {@code REPORT_VIEW} permission. Status bucket definitions are
 * backend-authoritative — see {@link ReportingService} for the mapping.
 */
@Tag(name = "Customer Reports", description = "Ticket statistics for a customer")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/customers/{customerId}/reports")
public class CustomerReportController {

    private static final Logger log = LoggerFactory.getLogger(CustomerReportController.class);

    private final ReportingService reportingService;

    public CustomerReportController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    /**
     * Returns ticket statistics for the given customer within the specified date range.
     *
     * <p>Counts are for tickets created within [from, to]. Status values reflect
     * current state at report generation time (not historical state).
     *
     * @param customerId customer to report on
     * @param from       start of window (ISO-8601), inclusive; omit for unbounded start
     * @param to         end of window (ISO-8601), inclusive; omit for unbounded end
     */
    @GetMapping("/tickets")
    @PreAuthorize("hasAuthority('PERM_REPORT_VIEW')")
    public ResponseEntity<CustomerTicketReportResponse> ticketReport(
            @PathVariable Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to) {
        log.info("GET /customers/{}/reports/tickets — from: {}, to: {}", customerId, from, to);
        return ResponseEntity.ok(reportingService.customerReport(customerId, from, to));
    }
}
