package com.caseflow.email.api;

import com.caseflow.common.exception.MailTemplateNotFoundException;
import com.caseflow.email.api.dto.MailTemplatePreviewRequest;
import com.caseflow.email.api.dto.MailTemplatePreviewResponse;
import com.caseflow.email.api.dto.MailTemplateRequest;
import com.caseflow.email.api.dto.MailTemplateResponse;
import com.caseflow.email.domain.MailTemplate;
import com.caseflow.email.service.MailTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailTemplateControllerTest {

    @Mock private MailTemplateService templateService;

    @InjectMocks
    private MailTemplateController sut;

    private MailTemplate template;

    @BeforeEach
    void setUp() {
        template = new MailTemplate();
        ReflectionTestUtils.setField(template, "id", 1L);
        template.setCode("CUSTOMER_REPLY");
        template.setName("Customer Reply");
        template.setHtmlTemplate("<html>body</html>");
        template.setPlainTextTemplate("body");
        template.setIsActive(true);
        template.setIsBuiltIn(true);
    }

    @Test
    void list_returnsAllTemplates() {
        when(templateService.findAll()).thenReturn(List.of(template));

        List<MailTemplateResponse> result = sut.list();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("CUSTOMER_REPLY");
    }

    @Test
    void get_returnsTemplate_byId() {
        when(templateService.findById(1L)).thenReturn(template);

        MailTemplateResponse result = sut.get(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.code()).isEqualTo("CUSTOMER_REPLY");
    }

    @Test
    void get_propagatesNotFoundException() {
        when(templateService.findById(99L)).thenThrow(new MailTemplateNotFoundException(99L));

        assertThatThrownBy(() -> sut.get(99L))
                .isInstanceOf(MailTemplateNotFoundException.class);
    }

    @Test
    void create_returns201_withCreatedTemplate() {
        MailTemplateRequest request = new MailTemplateRequest(
                "NEW_TMPL", "New Template", null,
                "<html>x</html>", "x", true);
        when(templateService.create(any())).thenReturn(template);

        ResponseEntity<MailTemplateResponse> result = sut.create(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
    }

    @Test
    void update_returnsUpdatedTemplate() {
        MailTemplateRequest request = new MailTemplateRequest(
                "CUSTOMER_REPLY", "Updated", null,
                "<html>new</html>", "new", true);
        when(templateService.update(eq(1L), any())).thenReturn(template);

        MailTemplateResponse result = sut.update(1L, request);

        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void delete_returns204_forCustomTemplate() {
        ResponseEntity<Void> result = sut.delete(1L);

        verify(templateService).delete(1L);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void delete_propagatesIllegalState_forBuiltIn() {
        doThrow(new IllegalStateException("Cannot delete built-in template"))
                .when(templateService).delete(1L);

        assertThatThrownBy(() -> sut.delete(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("built-in");
    }

    @Test
    void preview_returnsSubstitutedContent() {
        MailTemplatePreviewRequest previewReq = new MailTemplatePreviewRequest(
                "Hello", "TICKET-001", "Support", "Alice", null);
        when(templateService.preview(eq(1L), any()))
                .thenReturn(new MailTemplatePreviewResponse(null, "<html>Hello</html>", "Hello"));

        MailTemplatePreviewResponse result = sut.preview(1L, previewReq);

        assertThat(result.html()).contains("Hello");
        assertThat(result.text()).isEqualTo("Hello");
    }
}
