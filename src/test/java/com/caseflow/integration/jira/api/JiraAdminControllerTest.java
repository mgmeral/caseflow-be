package com.caseflow.integration.jira.api;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.integration.jira.domain.JiraConfig;
import com.caseflow.integration.jira.service.JiraConfigService;
import com.caseflow.ticket.security.TicketAuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JiraAdminController.class)
@Import(SecurityConfig.class)
class JiraAdminControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private JwtTokenService jwtTokenService;
    @MockBean private CaseFlowUserDetailsService userDetailsService;
    @MockBean private JiraConfigService configService;
    @MockBean(name = "ticketAuth") private TicketAuthorizationService ticketAuth;

    @Test
    @WithMockUser(authorities = "PERM_INTEGRATION_CONFIG_MANAGE")
    void getConfig_returns204_whenNoneConfigured() throws Exception {
        when(configService.findConfig()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/integrations/jira/config"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = "PERM_INTEGRATION_CONFIG_MANAGE")
    void getConfig_returnsConfig_withMaskedToken() throws Exception {
        JiraConfig config = buildConfig();
        when(configService.findConfig()).thenReturn(Optional.of(config));

        mockMvc.perform(get("/api/admin/integrations/jira/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectKey").value("TEST"))
                .andExpect(jsonPath("$.apiToken").value("****"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void getConfig_returns403_withoutPermission() throws Exception {
        mockMvc.perform(get("/api/admin/integrations/jira/config"))
                .andExpect(status().isForbidden());
    }

    @Test
    void saveConfig_returns200_withUpdatedConfig() throws Exception {
        JiraConfig saved = buildConfig();
        when(configService.save(any(), any(), any(), any(),
                any(), any(), any(), any(), anyBoolean(), anyLong()))
                .thenReturn(saved);

        Map<String, Object> body = Map.of(
                "baseUrl", "https://jira.example.com",
                "projectKey", "TEST",
                "enabled", true
        );

        mockMvc.perform(put("/api/admin/integrations/jira/config")
                        .with(user(adminPrincipal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectKey").value("TEST"));
    }

    @Test
    @WithMockUser(authorities = "PERM_INTEGRATION_CONFIG_MANAGE")
    void testConnection_returns200_onSuccess() throws Exception {
        when(configService.testConnection()).thenReturn(true);

        mockMvc.perform(post("/api/admin/integrations/jira/test").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CaseFlowUserDetails adminPrincipal() {
        CaseFlowUserDetails principal = mock(CaseFlowUserDetails.class);
        when(principal.getUserId()).thenReturn(1L);
        when(principal.getUsername()).thenReturn("admin");
        when(principal.getPassword()).thenReturn("");
        when(principal.isEnabled()).thenReturn(true);
        when(principal.isAccountNonExpired()).thenReturn(true);
        when(principal.isAccountNonLocked()).thenReturn(true);
        when(principal.isCredentialsNonExpired()).thenReturn(true);
        doReturn(List.of(new SimpleGrantedAuthority("PERM_INTEGRATION_CONFIG_MANAGE")))
                .when(principal).getAuthorities();
        return principal;
    }

    private JiraConfig buildConfig() {
        JiraConfig c = new JiraConfig();
        c.setIsEnabled(true);
        c.setBaseUrl("https://jira.example.com");
        c.setProjectKey("TEST");
        c.setIssueType("Task");
        c.setApiToken("secret-token");
        return c;
    }
}
