package com.caseflow.email.service;

import com.caseflow.common.exception.MailTemplateNotFoundException;
import com.caseflow.email.api.dto.MailTemplatePreviewRequest;
import com.caseflow.email.api.dto.MailTemplatePreviewResponse;
import com.caseflow.email.api.dto.MailTemplateRequest;
import com.caseflow.email.domain.MailTemplate;
import com.caseflow.email.repository.MailTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailTemplateServiceTest {

    @Mock private MailTemplateRepository templateRepository;

    @InjectMocks
    private MailTemplateService sut;

    private MailTemplate customTemplate;
    private MailTemplate builtInTemplate;

    @BeforeEach
    void setUp() {
        customTemplate = new MailTemplate();
        ReflectionTestUtils.setField(customTemplate, "id", 1L);
        customTemplate.setCode("CUSTOM_REPLY");
        customTemplate.setName("Custom Reply");
        customTemplate.setHtmlTemplate("<html>{replyBody}</html>");
        customTemplate.setPlainTextTemplate("{replyBody}\n---\nTicket: {ticketRef}");
        customTemplate.setIsActive(true);
        customTemplate.setIsBuiltIn(false);

        builtInTemplate = new MailTemplate();
        ReflectionTestUtils.setField(builtInTemplate, "id", 2L);
        builtInTemplate.setCode("CUSTOMER_REPLY");
        builtInTemplate.setName("Customer Reply");
        builtInTemplate.setHtmlTemplate("<html>{replyBody}</html>");
        builtInTemplate.setPlainTextTemplate("{replyBody}");
        builtInTemplate.setIsActive(true);
        builtInTemplate.setIsBuiltIn(true);
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Test
    void findAll_returnsTemplatesFromRepository() {
        when(templateRepository.findAllByOrderByCodeAsc()).thenReturn(List.of(customTemplate));

        List<MailTemplate> result = sut.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("CUSTOM_REPLY");
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_returnsTemplate_whenFound() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(customTemplate));

        MailTemplate result = sut.findById(1L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void findById_throws_whenNotFound() {
        when(templateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.findById(99L))
                .isInstanceOf(MailTemplateNotFoundException.class);
    }

    // ── findActiveByCode ──────────────────────────────────────────────────────

    @Test
    void findActiveByCode_returnsEmpty_whenNoActiveTemplate() {
        when(templateRepository.findByCodeAndIsActiveTrue(anyString())).thenReturn(Optional.empty());

        Optional<MailTemplate> result = sut.findActiveByCode("CUSTOMER_REPLY");

        assertThat(result).isEmpty();
    }

    @Test
    void findActiveByCode_returnsTemplate_whenActiveExists() {
        when(templateRepository.findByCodeAndIsActiveTrue("CUSTOMER_REPLY"))
                .thenReturn(Optional.of(builtInTemplate));

        Optional<MailTemplate> result = sut.findActiveByCode("CUSTOMER_REPLY");

        assertThat(result).isPresent();
        assertThat(result.get().getCode()).isEqualTo("CUSTOMER_REPLY");
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_savesTemplateWithUpperCaseCode() {
        MailTemplateRequest request = new MailTemplateRequest(
                "my_template", "My Template", null,
                "<html>body</html>", "body", true);
        when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MailTemplate result = sut.create(request);

        assertThat(result.getCode()).isEqualTo("MY_TEMPLATE");
        assertThat(result.getIsBuiltIn()).isFalse();
    }

    @Test
    void create_defaultsToActive_whenIsActiveNull() {
        MailTemplateRequest request = new MailTemplateRequest(
                "TMPL", "Tmpl", null, "<html/>", "text", null);
        when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MailTemplate result = sut.create(request);

        assertThat(result.getIsActive()).isTrue();
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_modifiesName_andHtmlTemplate() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(customTemplate));
        when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MailTemplateRequest request = new MailTemplateRequest(
                "CUSTOM_REPLY", "Updated Name", null,
                "<html>new</html>", "new text", true);

        MailTemplate result = sut.update(1L, request);

        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getHtmlTemplate()).isEqualTo("<html>new</html>");
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_removesCustomTemplate() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(customTemplate));

        sut.delete(1L);

        verify(templateRepository).delete(customTemplate);
    }

    @Test
    void delete_throws_whenTemplateIsBuiltIn() {
        when(templateRepository.findById(2L)).thenReturn(Optional.of(builtInTemplate));

        assertThatThrownBy(() -> sut.delete(2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("built-in");
    }

    // ── preview ───────────────────────────────────────────────────────────────

    @Test
    void preview_substitutesPlaceholders_inHtmlAndText() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(customTemplate));

        MailTemplatePreviewRequest previewReq = new MailTemplatePreviewRequest(
                "Hello!", "TICKET-123", null, null, null);

        MailTemplatePreviewResponse result = sut.preview(1L, previewReq);

        assertThat(result.html()).contains("Hello!");
        assertThat(result.text()).contains("Hello!");
        assertThat(result.text()).contains("TICKET-123");
    }

    @Test
    void preview_returnsNullSubject_whenSubjectTemplateIsNull() {
        when(templateRepository.findById(1L)).thenReturn(Optional.of(customTemplate));

        MailTemplatePreviewResponse result = sut.preview(1L,
                new MailTemplatePreviewRequest("body", "T-1", null, null, null));

        assertThat(result.subject()).isNull();
    }

    // ── substitute ───────────────────────────────────────────────────────────

    @Test
    void substitute_replacesAllPlaceholders() {
        MailTemplatePreviewRequest vars = new MailTemplatePreviewRequest(
                "body text", "TICKET-007", "Support Team", "Alice", "Best,\nAlice");

        String result = sut.substitute(
                "{replyBody} | {ticketRef} | {mailboxName} | {agentName} | {signatureBlock}", vars);

        assertThat(result).isEqualTo("body text | TICKET-007 | Support Team | Alice | Best,\nAlice");
    }

    @Test
    void substitute_treatsNullVarsAsEmpty() {
        MailTemplatePreviewRequest vars = new MailTemplatePreviewRequest(
                null, null, null, null, null);

        String result = sut.substitute("{replyBody}|{ticketRef}", vars);

        assertThat(result).isEqualTo("|");
    }

    @Test
    void substitute_returnsEmpty_forNullTemplate() {
        assertThat(sut.substitute(null,
                new MailTemplatePreviewRequest("b", "T", null, null, null))).isEmpty();
    }

    // ── Template content validation ───────────────────────────────────────────

    @Test
    void create_throws_whenHtmlTemplateContainsScript() {
        MailTemplateRequest request = new MailTemplateRequest(
                "BAD", "Bad", null,
                "<html><script>alert(1)</script></html>", "text", true);

        assertThatThrownBy(() -> sut.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("script");
    }

    @Test
    void create_throws_whenHtmlTemplateContainsIframe() {
        MailTemplateRequest request = new MailTemplateRequest(
                "BAD", "Bad", null,
                "<html><iframe src='x'></iframe></html>", "text", true);

        assertThatThrownBy(() -> sut.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("iframe");
    }

    @Test
    void create_throws_whenHtmlTemplateContainsJavascriptUri() {
        MailTemplateRequest request = new MailTemplateRequest(
                "BAD", "Bad", null,
                "<a href=\"javascript:void(0)\">click</a>", "text", true);

        assertThatThrownBy(() -> sut.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("javascript:");
    }

    @Test
    void update_throws_whenHtmlTemplateContainsScript() {
        MailTemplateRequest request = new MailTemplateRequest(
                "CUSTOM_REPLY", "Name", null,
                "<script>evil()</script>", "text", true);

        assertThatThrownBy(() -> sut.update(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("script");
    }

    @Test
    void create_accepts_safeHtmlTemplate() {
        MailTemplateRequest request = new MailTemplateRequest(
                "SAFE", "Safe", null,
                "<html><body><p>{replyBody}</p></body></html>", "body", true);
        when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Must not throw
        MailTemplate result = sut.create(request);

        assertThat(result.getCode()).isEqualTo("SAFE");
    }

    // ── escapeHtml ────────────────────────────────────────────────────────────

    @Test
    void escapeHtml_escapesSpecialCharacters() {
        String input = "<script>alert('xss')</script> & \"quoted\"";
        String escaped = sut.escapeHtml(input);

        assertThat(escaped).doesNotContain("<script>");
        assertThat(escaped).contains("&lt;script&gt;");
        assertThat(escaped).contains("&amp;");
        assertThat(escaped).contains("&quot;");
        assertThat(escaped).contains("&#x27;");
    }

    @Test
    void escapeHtml_returnsEmpty_forNull() {
        assertThat(sut.escapeHtml(null)).isEmpty();
    }

    @Test
    void escapeHtml_preservesPlainText() {
        String plain = "Hello, world! This is a normal message.";
        assertThat(sut.escapeHtml(plain)).isEqualTo(plain);
    }
}
