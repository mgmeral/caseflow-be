package com.caseflow.ai.api;

import com.caseflow.ai.service.AiTicketSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin operations on the AI similar-case index.
 */
@Tag(name = "AI Admin", description = "AI index maintenance")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/ai")
public class AiAdminController {

    private static final Logger log = LoggerFactory.getLogger(AiAdminController.class);

    private final AiTicketSyncService syncService;

    public AiAdminController(AiTicketSyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * Queues every RESOLVED/CLOSED ticket for (re)indexing — needed once after the vector
     * collection is created or recreated. Jobs run in the background on the integration-job
     * worker; re-running is safe (re-ingest replaces a ticket's chunks).
     */
    @Operation(summary = "Re-index all resolved/closed tickets for AI similar-case search")
    @PostMapping("/reindex")
    @PreAuthorize("hasAuthority('PERM_ADMIN_CONFIG')")
    public ResponseEntity<ReindexResponse> reindex() {
        int enqueued = syncService.reindexAll();
        log.info("POST /admin/ai/reindex — {} jobs enqueued", enqueued);
        return ResponseEntity.accepted().body(new ReindexResponse(enqueued));
    }

    /** @param enqueued sync jobs queued now (tickets that already had one waiting are not counted) */
    public record ReindexResponse(int enqueued) {}
}
