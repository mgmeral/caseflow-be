package com.caseflow.ai.api;

import com.caseflow.ai.service.AiDocumentSyncService;
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

    private final AiTicketSyncService ticketSyncService;
    private final AiDocumentSyncService documentSyncService;

    public AiAdminController(AiTicketSyncService ticketSyncService, AiDocumentSyncService documentSyncService) {
        this.ticketSyncService = ticketSyncService;
        this.documentSyncService = documentSyncService;
    }

    /**
     * Queues every RESOLVED/CLOSED ticket and every active knowledge document for (re)indexing —
     * needed once after the vector collection is created or recreated. Jobs run in the
     * background on the integration-job worker; re-running is safe (re-ingest replaces a
     * source's chunks).
     */
    @Operation(summary = "Re-index resolved/closed tickets and active knowledge documents for AI")
    @PostMapping("/reindex")
    @PreAuthorize("hasAuthority('PERM_ADMIN_CONFIG')")
    public ResponseEntity<ReindexResponse> reindex() {
        ReindexResponse response = new ReindexResponse(ticketSyncService.reindexAll(), documentSyncService.reindexAll());
        log.info("POST /admin/ai/reindex — {} ticket and {} document jobs enqueued", response.tickets(), response.documents());
        return ResponseEntity.accepted().body(response);
    }

    /** Sync jobs queued now (tickets that already had one waiting are not counted). */
    public record ReindexResponse(int tickets, int documents) {}
}
