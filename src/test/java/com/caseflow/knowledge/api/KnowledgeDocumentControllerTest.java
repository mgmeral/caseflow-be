package com.caseflow.knowledge.api;

import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.identity.domain.Permission;
import com.caseflow.knowledge.api.dto.KnowledgeDocumentRequest;
import com.caseflow.knowledge.domain.KnowledgeDocument;
import com.caseflow.knowledge.service.KnowledgeDocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KnowledgeDocumentController.class)
@Import(SecurityConfig.class)
class KnowledgeDocumentControllerTest {

    private static final String BODY = """
            {"title":"Refund policy","body":"Refunds within 30 days.","category":"Billing","customerId":null}""";

    @Autowired private MockMvc mockMvc;

    @MockBean private KnowledgeDocumentService service;
    @MockBean private JwtTokenService jwtTokenService;
    @MockBean private CaseFlowUserDetailsService userDetailsService;

    /** User id 7 (SecurityContextHelper parses numeric usernames) with the real ADMIN_CONFIG authority. */
    private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder builder) {
        return builder.with(user("7").authorities(new SimpleGrantedAuthority("PERM_" + Permission.ADMIN_CONFIG.name())))
                .with(csrf());
    }

    private static KnowledgeDocument doc() {
        KnowledgeDocument d = new KnowledgeDocument();
        d.setTitle("Refund policy");
        d.setBody("Refunds within 30 days.");
        d.setIsActive(true);
        return d;
    }

    @Test
    void create_asAdmin_returns201() throws Exception {
        when(service.create(any(), eq(7L))).thenReturn(doc());

        mockMvc.perform(asAdmin(post("/api/admin/knowledge-base")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Refund policy"))
                .andExpect(jsonPath("$.active").value(true));

        verify(service).create(eq(new KnowledgeDocumentRequest("Refund policy", "Refunds within 30 days.",
                "Billing", null, null)), eq(7L));
    }

    @Test
    void create_withoutAdminConfig_isForbidden() throws Exception {
        mockMvc.perform(post("/api/admin/knowledge-base")
                        .with(user("7").authorities(new SimpleGrantedAuthority("PERM_" + Permission.AI_ASSIST.name())))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());

        verify(service, never()).create(any(), any());
    }

    @Test
    void create_withBlankTitle_isRejected() throws Exception {
        mockMvc.perform(asAdmin(post("/api/admin/knowledge-base")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" \",\"body\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deactivate_andDelete_delegateToService() throws Exception {
        when(service.setActive(3L, false, 7L)).thenReturn(doc());

        mockMvc.perform(asAdmin(post("/api/admin/knowledge-base/3/deactivate"))).andExpect(status().isOk());
        mockMvc.perform(asAdmin(delete("/api/admin/knowledge-base/3"))).andExpect(status().isNoContent());

        verify(service).setActive(3L, false, 7L);
        verify(service).delete(3L, 7L);
    }
}
