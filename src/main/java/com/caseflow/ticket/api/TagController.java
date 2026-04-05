package com.caseflow.ticket.api;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.ticket.api.dto.TagRequest;
import com.caseflow.ticket.api.dto.TagResponse;
import com.caseflow.ticket.service.TagService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlled tag vocabulary management.
 *
 * <p>Creating, updating, and activating/deactivating tags requires {@code ADMIN_CONFIG} permission.
 * Listing active tags is accessible to any agent with ticket read permission.
 */
@Tag(name = "Tags", description = "Controlled tag vocabulary management")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/tags")
public class TagController {

    private static final Logger log = LoggerFactory.getLogger(TagController.class);

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_TICKET_READ')")
    public ResponseEntity<List<TagResponse>> list() {
        return ResponseEntity.ok(tagService.listActive());
    }

    @GetMapping("/all")
    @PreAuthorize("@ticketAuth.canManageTags(authentication)")
    public ResponseEntity<List<TagResponse>> listAll() {
        return ResponseEntity.ok(tagService.listAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_TICKET_READ')")
    public ResponseEntity<TagResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(tagService.getById(id));
    }

    @PostMapping
    @PreAuthorize("@ticketAuth.canManageTags(authentication)")
    public ResponseEntity<TagResponse> create(@Valid @RequestBody TagRequest request,
                                              @AuthenticationPrincipal CaseFlowUserDetails principal) {
        log.info("POST /tags — code: '{}'", request.code());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tagService.create(request, principal.getUserId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ticketAuth.canManageTags(authentication)")
    public ResponseEntity<TagResponse> update(@PathVariable Long id,
                                              @Valid @RequestBody TagRequest request,
                                              @AuthenticationPrincipal CaseFlowUserDetails principal) {
        log.info("PUT /tags/{}", id);
        return ResponseEntity.ok(tagService.update(id, request, principal.getUserId()));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("@ticketAuth.canManageTags(authentication)")
    public ResponseEntity<TagResponse> activate(@PathVariable Long id,
                                                @AuthenticationPrincipal CaseFlowUserDetails principal) {
        log.info("PATCH /tags/{}/activate", id);
        return ResponseEntity.ok(tagService.activate(id, principal.getUserId()));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("@ticketAuth.canManageTags(authentication)")
    public ResponseEntity<TagResponse> deactivate(@PathVariable Long id,
                                                  @AuthenticationPrincipal CaseFlowUserDetails principal) {
        log.info("PATCH /tags/{}/deactivate", id);
        return ResponseEntity.ok(tagService.deactivate(id, principal.getUserId()));
    }
}
