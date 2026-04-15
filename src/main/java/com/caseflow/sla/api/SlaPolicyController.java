package com.caseflow.sla.api;

import com.caseflow.sla.api.dto.SlaPolicyRequest;
import com.caseflow.sla.api.dto.SlaPolicyResponse;
import com.caseflow.sla.service.SlaBackfillService;
import com.caseflow.sla.service.SlaService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CRUD management for SLA policy configurations.
 *
 * <p>Policies are applied to new tickets at creation time to set
 * {@code firstResponseDueAt} and {@code resolutionDueAt}.
 * Changing a policy does NOT retroactively update already-created tickets.
 */
@Tag(name = "SLA Policies", description = "SLA policy configuration management")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/sla/policies")
public class SlaPolicyController {

    private final SlaService slaService;
    private final SlaBackfillService slaBackfillService;

    public SlaPolicyController(SlaService slaService, SlaBackfillService slaBackfillService) {
        this.slaService = slaService;
        this.slaBackfillService = slaBackfillService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
    public ResponseEntity<List<SlaPolicyResponse>> list() {
        return ResponseEntity.ok(slaService.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
    public ResponseEntity<SlaPolicyResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(slaService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
    public ResponseEntity<SlaPolicyResponse> create(@Valid @RequestBody SlaPolicyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(slaService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
    public ResponseEntity<SlaPolicyResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody SlaPolicyRequest request) {
        return ResponseEntity.ok(slaService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        slaService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Backfills SLA due dates for non-terminal tickets where {@code resolutionDueAt} is null.
     *
     * <p>Safe to run multiple times — only processes tickets with null due dates.
     * Use this after policy changes or as a post-deploy repair step if the V35 migration
     * was applied when no active policy existed yet.
     *
     * <p>Returns a summary: how many tickets were stamped, skipped (no policy), or failed.
     */
    @PostMapping("/backfill")
    @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
    public ResponseEntity<SlaBackfillService.BackfillResult> backfill() {
        return ResponseEntity.ok(slaBackfillService.backfillMissingDueDates());
    }
}
