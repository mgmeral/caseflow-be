package com.caseflow.email.service;

import com.caseflow.email.api.dto.ReplyPreviewRequest;
import com.caseflow.email.api.dto.ReplyPreviewResponse;
import com.caseflow.email.domain.EmailIngressEvent;
import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.domain.MailTemplate;
import com.caseflow.email.repository.EmailIngressEventRepository;
import com.caseflow.ticket.domain.Ticket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Renders a reply preview for the agent to review before sending.
 *
 * <p>Applies the same template resolution and placeholder substitution as the
 * real send path, so the preview is faithful to what will actually be sent.
 *
 * <h2>Placeholder governance</h2>
 * Known placeholders: {replyBody}, {ticketRef}, {mailboxName}, {agentName}, {signatureBlock}.
 * Any unknown placeholder is reported as a diagnostic warning (severity UNKNOWN).
 * Empty-value placeholders that had a known key are reported as EMPTY.
 * Preview is never hard-failed for placeholder issues — warnings guide the agent.
 */
@Service
public class ReplyPreviewService {

    private static final Logger log = LoggerFactory.getLogger(ReplyPreviewService.class);

    private static final String DEFAULT_TEMPLATE_CODE = "CUSTOMER_REPLY";

    /** All known placeholders used by the template engine. */
    private static final List<String> KNOWN_PLACEHOLDERS = List.of(
            "{replyBody}", "{ticketRef}", "{mailboxName}", "{agentName}", "{signatureBlock}");

    private final EmailIngressEventRepository ingressEventRepository;
    private final EmailMailboxService mailboxService;
    private final MailTemplateService mailTemplateService;

    public ReplyPreviewService(EmailIngressEventRepository ingressEventRepository,
                               EmailMailboxService mailboxService,
                               MailTemplateService mailTemplateService) {
        this.ingressEventRepository = ingressEventRepository;
        this.mailboxService = mailboxService;
        this.mailTemplateService = mailTemplateService;
    }

    /**
     * Generates a rendered reply preview for the given ticket and request.
     *
     * @param ticket    the target ticket (pre-validated by caller)
     * @param request   preview parameters from the FE
     * @param actorName display name of the authenticated agent (for {agentName} substitution)
     */
    @Transactional(readOnly = true)
    public ReplyPreviewResponse preview(Ticket ticket, ReplyPreviewRequest request, String actorName) {
        // Resolve and validate mailbox
        EmailMailbox mailbox = resolveMailbox(request.mailboxId());
        if (!Boolean.TRUE.equals(mailbox.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mailbox " + request.mailboxId() + " is not active");
        }
        String mailboxDisplayName = mailbox.getDisplayName() != null
                ? mailbox.getDisplayName() : mailbox.getName();

        // Resolve source event and derive recipient
        EmailIngressEvent sourceEvent = resolveSourceEvent(request.sourceEventId(), ticket.getId());
        String derivedToAddress = deriveRecipient(sourceEvent, ticket);

        // Resolve template
        MailTemplate template = resolveTemplate(request.templateId(), request.templateCode());

        // Collect substitution values
        String replyBody = request.bodyText() != null ? request.bodyText() : "";
        String ticketRef = ticket.getTicketNo();

        List<String> warnings = new ArrayList<>();
        List<ReplyPreviewResponse.PlaceholderDiagnostic> diagnostics = new ArrayList<>();

        // Render subject
        String subjectTemplate = template != null ? template.getSubjectTemplate() : null;
        String renderedSubject = request.subjectOverride() != null
                ? request.subjectOverride()
                : (subjectTemplate != null
                        ? applySubstitution(subjectTemplate, replyBody, ticketRef,
                                mailboxDisplayName, actorName, null, diagnostics)
                        : "Re: " + ticket.getSubject());

        // Render body
        String renderedText;
        String renderedHtml;

        if (template != null) {
            renderedText = applySubstitution(template.getPlainTextTemplate(),
                    replyBody, ticketRef, mailboxDisplayName, actorName, null, diagnostics);
            String bodyForHtml = (request.bodyHtml() != null && !request.bodyHtml().isBlank())
                    ? request.bodyHtml()
                    : mailTemplateService.escapeHtml(request.bodyText());
            renderedHtml = applySubstitution(template.getHtmlTemplate(),
                    bodyForHtml, ticketRef, mailboxDisplayName, actorName, null, diagnostics);
        } else {
            warnings.add("No active template found — using built-in fallback renderer");
            renderedText = replyBody + "\n\n---\nTicket: " + ticketRef;
            renderedHtml = null;
        }

        // Check for remaining unresolved placeholders in rendered output
        checkUnresolvedPlaceholders(renderedText, diagnostics);
        checkUnresolvedPlaceholders(renderedHtml, diagnostics);

        String sourceDetailType = sourceEvent != null ? "EMAIL_DOCUMENT" : null;
        String sourceDetailId = sourceEvent != null && sourceEvent.getDocumentId() != null
                ? sourceEvent.getDocumentId()
                : (sourceEvent != null ? String.valueOf(sourceEvent.getId()) : null);

        ReplyPreviewResponse.TemplateInfo templateInfo = template != null
                ? new ReplyPreviewResponse.TemplateInfo(template.getId(), template.getCode(), template.getName())
                : null;

        log.info("REPLY_PREVIEW ticket: {}, mailbox: {}, template: {}, to: '{}'",
                ticket.getId(), request.mailboxId(),
                template != null ? template.getCode() : "FALLBACK",
                derivedToAddress);

        return new ReplyPreviewResponse(
                ticket.getPublicId(),
                sourceDetailType,
                sourceDetailId,
                mailbox.getId(),
                mailboxDisplayName,
                mailbox.getAddress(),
                derivedToAddress,
                mailbox.getAddress(),
                renderedSubject,
                renderedText,
                renderedHtml,
                templateInfo,
                warnings,
                diagnostics,
                true,  // always editable
                Instant.now()
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private EmailMailbox resolveMailbox(Long mailboxId) {
        try {
            return mailboxService.getById(mailboxId);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mailbox " + mailboxId + " not found");
        }
    }

    private EmailIngressEvent resolveSourceEvent(Long sourceEventId, Long ticketId) {
        if (sourceEventId == null) return null;
        EmailIngressEvent event = ingressEventRepository.findById(sourceEventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Source ingress event not found: " + sourceEventId));
        if (!ticketId.equals(event.getTicketId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Source event " + sourceEventId + " does not belong to this ticket");
        }
        return event;
    }

    private String deriveRecipient(EmailIngressEvent sourceEvent, Ticket ticket) {
        if (sourceEvent != null) {
            String derived = sourceEvent.effectiveReplyTo();
            if (derived != null && !derived.isBlank()) {
                return extractEmailAddress(derived);
            }
        }
        // No source event — proactive outreach, return placeholder
        return null;
    }

    private MailTemplate resolveTemplate(Long templateId, String templateCode) {
        if (templateId != null) {
            try {
                MailTemplate t = mailTemplateService.findById(templateId);
                if (Boolean.TRUE.equals(t.getIsActive())) return t;
            } catch (Exception e) {
                log.warn("PREVIEW_WARN templateId {} not found — falling back", templateId);
            }
        }
        String codeToUse = (templateCode != null && !templateCode.isBlank())
                ? templateCode : DEFAULT_TEMPLATE_CODE;
        Optional<MailTemplate> byCode = mailTemplateService.findActiveByCode(codeToUse);
        if (byCode.isPresent()) return byCode.get();
        if (!DEFAULT_TEMPLATE_CODE.equals(codeToUse)) {
            return mailTemplateService.findActiveByCode(DEFAULT_TEMPLATE_CODE).orElse(null);
        }
        return null;
    }

    /**
     * Applies the same substitution as {@link MailTemplateService#substitute} but tracks
     * diagnostics for any values that substituted as empty.
     */
    private String applySubstitution(String template, String replyBody, String ticketRef,
                                     String mailboxName, String agentName, String signatureBlock,
                                     List<ReplyPreviewResponse.PlaceholderDiagnostic> diagnostics) {
        if (template == null) return "";
        String result = mailTemplateService.substitute(template, replyBody, ticketRef,
                mailboxName, agentName, signatureBlock);

        // Report empty-value placeholders
        reportEmptyIfBlank("{replyBody}", replyBody, diagnostics);
        reportEmptyIfBlank("{ticketRef}", ticketRef, diagnostics);
        reportEmptyIfBlank("{mailboxName}", mailboxName, diagnostics);
        reportEmptyIfBlank("{agentName}", agentName, diagnostics);

        return result;
    }

    private void reportEmptyIfBlank(String placeholder, String value,
                                    List<ReplyPreviewResponse.PlaceholderDiagnostic> diagnostics) {
        if (value == null || value.isBlank()) {
            diagnostics.add(new ReplyPreviewResponse.PlaceholderDiagnostic(
                    placeholder, "EMPTY",
                    placeholder + " resolved to empty — the rendered output may look incomplete"));
        }
    }

    /** Scans rendered output for any unresolved {placeholder} patterns. */
    private void checkUnresolvedPlaceholders(String rendered,
                                             List<ReplyPreviewResponse.PlaceholderDiagnostic> diagnostics) {
        if (rendered == null) return;
        int start = 0;
        while ((start = rendered.indexOf('{', start)) >= 0) {
            int end = rendered.indexOf('}', start);
            if (end < 0) break;
            String candidate = rendered.substring(start, end + 1);
            if (!KNOWN_PLACEHOLDERS.contains(candidate)) {
                diagnostics.add(new ReplyPreviewResponse.PlaceholderDiagnostic(
                        candidate, "UNKNOWN",
                        candidate + " is not a recognized template placeholder"));
            }
            start = end + 1;
        }
    }

    private String extractEmailAddress(String raw) {
        if (raw == null) return null;
        int s = raw.lastIndexOf('<');
        int e = raw.lastIndexOf('>');
        if (s >= 0 && e > s) return raw.substring(s + 1, e).trim();
        return raw.trim();
    }
}
