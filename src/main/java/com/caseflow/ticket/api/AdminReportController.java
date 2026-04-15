package com.caseflow.ticket.api;

import com.caseflow.common.api.PagedResponse;
import com.caseflow.ticket.api.dto.AdminCustomerReportRow;
import com.caseflow.ticket.api.dto.AdminReportSummaryResponse;
import com.caseflow.ticket.api.dto.AgingBucketsResponse;
import com.caseflow.ticket.api.dto.CustomerHealthSummary;
import com.caseflow.ticket.api.dto.TrendDataPoint;
import com.caseflow.ticket.api.dto.WorkloadSummaryResponse;
import com.caseflow.ticket.service.ReportingService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Admin cross-customer aggregate ticket reporting.
 *
 * <p>Requires {@code REPORT_VIEW} permission. Returns one row per customer
 * with status bucket counts for the specified date window.
 *
 * <p>Status bucket semantics are backend-authoritative — see {@link ReportingService}.
 */
@Tag(name = "Admin Reports", description = "Cross-customer aggregate ticket statistics")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private static final Logger log = LoggerFactory.getLogger(AdminReportController.class);

    private final ReportingService reportingService;

    public AdminReportController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    /**
     * Cross-customer aggregate ticket report.
     *
     * <p>Returns one row per customer with ticket counts by status bucket.
     * Counts reflect tickets created within [from, to] at their current state.
     *
     * @param from     start of window (ISO-8601), inclusive; omit for unbounded start
     * @param to       end of window (ISO-8601), inclusive; omit for unbounded end
     * @param page     0-based page number (default 0)
     * @param size     page size (default 20)
     * @param sortBy   sort field: customerName or totalCount (default customerName)
     * @param sortDir  asc or desc (default asc)
     */
    @GetMapping("/customers/tickets")
    @PreAuthorize("hasAuthority('PERM_REPORT_VIEW')")
    public ResponseEntity<PagedResponse<AdminCustomerReportRow>> aggregateReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        log.info("GET /admin/reports/customers/tickets — from: {}, to: {}, page: {}, size: {}",
                from, to, page, size);

        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        // totalCount is a computed field and cannot be sorted at DB level.
        // customerName maps to the JPA 'name' field. All other values fall back to 'name'.
        String entityField = "name";
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, entityField));

        Page<AdminCustomerReportRow> result = reportingService.adminAggregateReport(from, to, pageable);

        return ResponseEntity.ok(PagedResponse.from(result));
    }

    /**
     * Executive summary — single-customer or global ticket KPI block.
     *
     * @param customerId optional; omit for global (all-customer) summary
     * @param from       start of window (ISO-8601), inclusive; omit for unbounded
     * @param to         end of window (ISO-8601), inclusive; omit for unbounded
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('PERM_REPORT_VIEW')")
    public ResponseEntity<AdminReportSummaryResponse> executiveSummary(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to) {

        log.info("GET /admin/reports/summary — customerId: {}, from: {}, to: {}", customerId, from, to);
        return ResponseEntity.ok(reportingService.executiveSummary(customerId, from, to));
    }

    /**
     * Daily ticket volume trend — created, resolved, and closed counts per calendar day.
     *
     * @param customerId optional customer filter; omit for global
     * @param from       start of window; omit for last 30 days (server default)
     * @param to         end of window; omit for today
     */
    @GetMapping("/trend")
    @PreAuthorize("hasAuthority('PERM_REPORT_VIEW')")
    public ResponseEntity<List<TrendDataPoint>> dailyTrend(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to) {

        log.info("GET /admin/reports/trend — customerId: {}, from: {}, to: {}", customerId, from, to);
        return ResponseEntity.ok(reportingService.dailyTrend(customerId, from, to));
    }

    /**
     * Backlog aging distribution — open ticket counts bucketed by age from creation.
     * Buckets: &lt;4h, 4–24h, 1–3d, 3–7d, &gt;7d.
     *
     * @param customerId optional customer filter; omit for global
     */
    @GetMapping("/aging")
    @PreAuthorize("hasAuthority('PERM_REPORT_VIEW')")
    public ResponseEntity<AgingBucketsResponse> agingBuckets(
            @RequestParam(required = false) Long customerId) {

        log.info("GET /admin/reports/aging — customerId: {}", customerId);
        return ResponseEntity.ok(reportingService.agingBuckets(customerId));
    }

    /**
     * Workload summary — active ticket counts per assignee and per group.
     * Includes unassigned and waiting-customer breakdowns.
     */
    @GetMapping("/workload")
    @PreAuthorize("hasAuthority('PERM_REPORT_VIEW')")
    public ResponseEntity<WorkloadSummaryResponse> workloadSummary() {
        log.info("GET /admin/reports/workload");
        return ResponseEntity.ok(reportingService.workloadSummary());
    }

    /**
     * Customer health summary — per-customer open count, breached SLA, avg response times,
     * and a health score (STABLE / WATCH / AT_RISK).
     *
     * <p>Returns all customers in a single response. Suitable for management overview dashboards.
     */
    @GetMapping("/health")
    @PreAuthorize("hasAuthority('PERM_REPORT_VIEW')")
    public ResponseEntity<List<CustomerHealthSummary>> customerHealth() {
        log.info("GET /admin/reports/health");
        return ResponseEntity.ok(reportingService.customerHealthSummaries());
    }
}
