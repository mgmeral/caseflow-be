package com.caseflow.knowledge.api;

import com.caseflow.common.api.PagedResponse;
import com.caseflow.common.security.SecurityContextHelper;
import com.caseflow.knowledge.api.dto.KnowledgeDocumentRequest;
import com.caseflow.knowledge.api.dto.KnowledgeDocumentResponse;
import com.caseflow.knowledge.service.KnowledgeDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

/**
 * Admin management of knowledge-base documents — the policy source for AI policy guidance and
 * reply-draft grounding. Changes reach the AI index asynchronously via the integration-job queue.
 */
@Tag(name = "Knowledge Base", description = "Policy / knowledge documents used by AI assist")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/knowledge-base")
@PreAuthorize("hasAuthority('PERM_ADMIN_CONFIG')")
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService service;

    public KnowledgeDocumentController(KnowledgeDocumentService service) {
        this.service = service;
    }

    @Operation(summary = "List knowledge documents, most recently updated first")
    @GetMapping
    public ResponseEntity<PagedResponse<KnowledgeDocumentResponse>> list(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(service.list(pageable).map(KnowledgeDocumentResponse::from)));
    }

    @Operation(summary = "Get a knowledge document")
    @GetMapping("/{id}")
    public ResponseEntity<KnowledgeDocumentResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(KnowledgeDocumentResponse.from(service.get(id)));
    }

    @Operation(summary = "Create a knowledge document (indexed for AI unless inactive)")
    @PostMapping
    public ResponseEntity<KnowledgeDocumentResponse> create(@Valid @RequestBody KnowledgeDocumentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(KnowledgeDocumentResponse.from(
                service.create(request, SecurityContextHelper.requireCurrentUserId())));
    }

    @Operation(summary = "Update a knowledge document (re-indexed for AI)")
    @PutMapping("/{id}")
    public ResponseEntity<KnowledgeDocumentResponse> update(@PathVariable Long id,
                                                            @Valid @RequestBody KnowledgeDocumentRequest request) {
        return ResponseEntity.ok(KnowledgeDocumentResponse.from(
                service.update(id, request, SecurityContextHelper.requireCurrentUserId())));
    }

    @Operation(summary = "Activate a knowledge document (added to the AI index)")
    @PostMapping("/{id}/activate")
    public ResponseEntity<KnowledgeDocumentResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(KnowledgeDocumentResponse.from(
                service.setActive(id, true, SecurityContextHelper.requireCurrentUserId())));
    }

    @Operation(summary = "Deactivate a knowledge document (removed from the AI index, kept here)")
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<KnowledgeDocumentResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(KnowledgeDocumentResponse.from(
                service.setActive(id, false, SecurityContextHelper.requireCurrentUserId())));
    }

    @Operation(summary = "Delete a knowledge document (removed from the AI index)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id, SecurityContextHelper.requireCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
