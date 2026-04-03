package com.caseflow.email.service;

import com.caseflow.common.exception.MailTemplateNotFoundException;
import com.caseflow.email.api.dto.MailTemplatePreviewRequest;
import com.caseflow.email.api.dto.MailTemplatePreviewResponse;
import com.caseflow.email.api.dto.MailTemplateRequest;
import com.caseflow.email.domain.MailTemplate;
import com.caseflow.email.repository.MailTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * CRUD management for {@link MailTemplate} entities.
 *
 * <p>Built-in templates ({@code isBuiltIn=true}) are seeded by Flyway and may be edited
 * but not deleted — they represent the structural defaults for each outbound email type.
 * Custom templates created via API have {@code isBuiltIn=false}.
 *
 * <p>Placeholder substitution is applied by {@link #substitute} and used directly by callers.
 */
@Service
public class MailTemplateService {

    private static final Logger log = LoggerFactory.getLogger(MailTemplateService.class);

    private final MailTemplateRepository templateRepository;

    public MailTemplateService(MailTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @Transactional(readOnly = true)
    public List<MailTemplate> findAll() {
        return templateRepository.findAllByOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public MailTemplate findById(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new MailTemplateNotFoundException(id));
    }

    /**
     * Returns the active template for the given code, if one exists in the database.
     * Returns empty when no DB template is configured — callers should fall back to the
     * built-in hardcoded renderer.
     */
    @Transactional(readOnly = true)
    public Optional<MailTemplate> findActiveByCode(String code) {
        return templateRepository.findByCodeAndIsActiveTrue(code);
    }

    @Transactional
    public MailTemplate create(MailTemplateRequest request) {
        validateTemplateContent(request.htmlTemplate());
        MailTemplate template = new MailTemplate();
        template.setCode(request.code().toUpperCase());
        template.setName(request.name());
        template.setSubjectTemplate(request.subjectTemplate());
        template.setHtmlTemplate(request.htmlTemplate());
        template.setPlainTextTemplate(request.plainTextTemplate());
        template.setIsActive(request.isActive() != null ? request.isActive() : true);
        template.setIsBuiltIn(false);
        MailTemplate saved = templateRepository.save(template);
        log.info("TEMPLATE_CRUD create — templateId: {}, code: '{}'", saved.getId(), saved.getCode());
        return saved;
    }

    @Transactional
    public MailTemplate update(Long id, MailTemplateRequest request) {
        validateTemplateContent(request.htmlTemplate());
        MailTemplate template = findById(id);
        template.setName(request.name());
        template.setSubjectTemplate(request.subjectTemplate());
        template.setHtmlTemplate(request.htmlTemplate());
        template.setPlainTextTemplate(request.plainTextTemplate());
        if (request.isActive() != null) {
            template.setIsActive(request.isActive());
        }
        // code is immutable after creation to preserve cross-system references
        MailTemplate saved = templateRepository.save(template);
        log.info("TEMPLATE_CRUD update — templateId: {}, code: '{}'", saved.getId(), saved.getCode());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        MailTemplate template = findById(id);
        if (Boolean.TRUE.equals(template.getIsBuiltIn())) {
            throw new IllegalStateException(
                    "Cannot delete built-in template '" + template.getCode()
                            + "' — deactivate it instead");
        }
        templateRepository.delete(template);
        log.info("TEMPLATE_CRUD delete — templateId: {}, code: '{}'", id, template.getCode());
    }

    /**
     * Renders a preview of the given template with the provided sample data.
     * Applies the same placeholder substitution used in production rendering.
     */
    @Transactional(readOnly = true)
    public MailTemplatePreviewResponse preview(Long id, MailTemplatePreviewRequest request) {
        MailTemplate template = findById(id);
        String html = substitute(template.getHtmlTemplate(), request);
        String text = substitute(template.getPlainTextTemplate(), request);
        String subject = template.getSubjectTemplate() != null
                ? substitute(template.getSubjectTemplate(), request)
                : null;
        return new MailTemplatePreviewResponse(subject, html, text);
    }

    // ── Placeholder substitution ──────────────────────────────────────────────

    /**
     * Applies placeholder substitution to a template body from a preview request.
     * Placeholders: {replyBody}, {ticketRef}, {mailboxName}, {agentName}, {signatureBlock}.
     * Unknown placeholders are left as-is.
     */
    public String substitute(String template, MailTemplatePreviewRequest vars) {
        if (template == null) return "";
        return substitute(template,
                vars.replyBody(), vars.ticketRef(), vars.mailboxName(),
                vars.agentName(), vars.signatureBlock());
    }

    /**
     * Applies placeholder substitution to a template body from individual values.
     * Use this overload to avoid importing {@link MailTemplatePreviewRequest} in the template layer.
     */
    public String substitute(String template,
                              String replyBody, String ticketRef,
                              String mailboxName, String agentName, String signatureBlock) {
        if (template == null) return "";
        return template
                .replace("{replyBody}", safe(replyBody))
                .replace("{ticketRef}", safe(ticketRef))
                .replace("{mailboxName}", safe(mailboxName))
                .replace("{agentName}", safe(agentName))
                .replace("{signatureBlock}", safe(signatureBlock));
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }

    /**
     * Validates that an admin-managed HTML template body does not contain
     * dangerous active content (script, iframe, javascript: URL).
     *
     * <p>Trust boundary: operator-authored templates are trusted for layout and styling
     * but must not introduce executable scripts that could harm recipients or future
     * preview consumers.
     *
     * @throws IllegalArgumentException if the template contains disallowed content
     */
    private static void validateTemplateContent(String htmlTemplate) {
        if (htmlTemplate == null) return;
        String lower = htmlTemplate.toLowerCase();
        if (lower.contains("<script") || lower.contains("</script")) {
            throw new IllegalArgumentException(
                    "Template HTML must not contain <script> elements");
        }
        if (lower.contains("<iframe")) {
            throw new IllegalArgumentException(
                    "Template HTML must not contain <iframe> elements");
        }
        if (lower.contains("javascript:")) {
            throw new IllegalArgumentException(
                    "Template HTML must not contain javascript: URIs");
        }
    }

    /**
     * HTML-escapes a plain-text string so it can be safely embedded inside an HTML element.
     * Converts {@code &}, {@code <}, {@code >}, {@code "}, and {@code '} to their HTML entities.
     * Preserves line breaks as-is (the surrounding template uses {@code white-space:pre-wrap}).
     */
    public String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
