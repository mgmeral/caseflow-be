package com.caseflow.ai.api;

import com.caseflow.ai.api.dto.AiPolicyGuidanceAssistResponse;
import com.caseflow.ai.api.dto.AiPolicyGuidanceRequest;
import com.caseflow.ai.api.dto.AiReplyDraftAssistResponse;
import com.caseflow.ai.api.dto.AiReplyDraftRequest;
import com.caseflow.ai.api.dto.AiSimilarCasesAssistResponse;
import com.caseflow.ai.api.dto.AiSummaryAssistResponse;
import com.caseflow.ai.service.AiAssistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BE-owned AI assist endpoints for ticket-level operations.
 *
 * <h2>Architecture rules enforced here</h2>
 * <ul>
 *   <li>FE calls CaseFlow BE — never the AI service directly</li>
 *   <li>All endpoints require authentication + PERM_AI_ASSIST + ticket visibility</li>
 *   <li>Responses are stable BE-owned DTOs, not raw AI service payloads</li>
 *   <li>AI failures return graceful 200 responses with {@code metadata.available=false}</li>
 *   <li>AI must never auto-send email, change status, or assign tickets</li>
 * </ul>
 */
@Tag(name = "AI Assist", description = "AI-powered ticket assist — summary, reply draft, similar cases, policy guidance")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/tickets/{ticketId}")
public class AiAssistantController {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantController.class);

    private final AiAssistService aiAssistService;

    public AiAssistantController(AiAssistService aiAssistService) {
        this.aiAssistService = aiAssistService;
    }

    /**
     * Generates an AI summary for the given ticket.
     *
     * <p>Returns a structured unavailable response (200) if the AI service is down
     * rather than a 5xx error. The FE must check {@code metadata.available}.
     */
    @Operation(summary = "Generate AI summary for a ticket")
    @GetMapping("/ai-summary")
    @PreAuthorize("hasAuthority('PERM_AI_ASSIST') and @ticketAuth.canReadTicket(authentication, #ticketId)")
    public ResponseEntity<AiSummaryAssistResponse> summarize(@PathVariable Long ticketId) {
        log.info("GET /tickets/{}/ai-summary", ticketId);
        return ResponseEntity.ok(aiAssistService.summarize(ticketId));
    }

    /**
     * Generates an AI reply-draft suggestion for the given ticket.
     *
     * <p>The draft is a suggestion only — the agent must review and approve before
     * sending. CaseFlow BE never auto-sends an AI draft.
     */
    @Operation(summary = "Generate AI reply draft suggestion for a ticket")
    @PostMapping("/ai-reply-draft")
    @PreAuthorize("hasAuthority('PERM_AI_ASSIST') and @ticketAuth.canReadTicket(authentication, #ticketId)")
    public ResponseEntity<AiReplyDraftAssistResponse> replyDraft(
            @PathVariable Long ticketId,
            @RequestBody(required = false) AiReplyDraftRequest request) {
        log.info("POST /tickets/{}/ai-reply-draft", ticketId);
        String toneHint = request != null ? request.toneHint() : null;
        return ResponseEntity.ok(aiAssistService.replyDraft(ticketId, toneHint));
    }

    /**
     * Retrieves AI-matched similar resolved cases for the given ticket.
     */
    @Operation(summary = "Find AI-similar resolved cases for a ticket")
    @GetMapping("/ai-similar-cases")
    @PreAuthorize("hasAuthority('PERM_AI_ASSIST') and @ticketAuth.canReadTicket(authentication, #ticketId)")
    public ResponseEntity<AiSimilarCasesAssistResponse> similarCases(@PathVariable Long ticketId) {
        log.info("GET /tickets/{}/ai-similar-cases", ticketId);
        return ResponseEntity.ok(aiAssistService.similarCases(ticketId));
    }

    /**
     * Retrieves AI-powered policy guidance relevant to the given ticket and question.
     */
    @Operation(summary = "Get AI policy guidance for a ticket")
    @PostMapping("/ai-policy-guidance")
    @PreAuthorize("hasAuthority('PERM_AI_ASSIST') and @ticketAuth.canReadTicket(authentication, #ticketId)")
    public ResponseEntity<AiPolicyGuidanceAssistResponse> policyGuidance(
            @PathVariable Long ticketId,
            @Valid @RequestBody AiPolicyGuidanceRequest request) {
        log.info("POST /tickets/{}/ai-policy-guidance", ticketId);
        return ResponseEntity.ok(aiAssistService.policyGuidance(ticketId, request.question()));
    }
}
