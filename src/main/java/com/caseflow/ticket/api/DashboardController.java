package com.caseflow.ticket.api;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.ticket.api.dto.DashboardStatsResponse;
import com.caseflow.ticket.service.DashboardService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard", description = "Aggregate dashboard statistics")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Returns aggregate ticket statistics for the dashboard.
     *
     * <p>All counts use explicit, documented business rules — see {@link DashboardStatsResponse}
     * for field semantics. The {@code myActionRequired} widget is scoped to the authenticated caller.
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('PERM_TICKET_READ')")
    public ResponseEntity<DashboardStatsResponse> getStats(
            @AuthenticationPrincipal CaseFlowUserDetails user) {
        Long userId = user != null ? user.getUserId() : null;
        log.info("GET /dashboard/stats — userId: {}", userId);
        return ResponseEntity.ok(dashboardService.getStats(userId));
    }
}
