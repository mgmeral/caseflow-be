package com.caseflow.automation.api;

import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.automation.domain.AutomationRule;
import com.caseflow.automation.domain.AutomationTriggerType;
import com.caseflow.automation.repository.AutomationRuleRepository;
import com.caseflow.common.security.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AutomationRuleController.class)
@Import(SecurityConfig.class)
class AutomationRuleControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AutomationRuleRepository ruleRepository;
    @MockBean private JwtTokenService jwtTokenService;
    @MockBean private CaseFlowUserDetailsService userDetailsService;

    // ── GET /admin/automation/rules ───────────────────────────────────────────

    @Test
    void list_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/admin/automation/rules"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void list_returns403_whenMissingPermission() throws Exception {
        mockMvc.perform(get("/api/admin/automation/rules"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "PERM_SETTINGS_MANAGE")
    void list_returns200_withRules() throws Exception {
        AutomationRule rule = rule("Auto-assign HIGH", AutomationTriggerType.TICKET_CREATED);
        when(ruleRepository.findAllByOrderByTriggerTypeAscExecutionOrderAsc())
                .thenReturn(List.of(rule));

        mockMvc.perform(get("/api/admin/automation/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Auto-assign HIGH"))
                .andExpect(jsonPath("$[0].triggerType").value("TICKET_CREATED"));
    }

    @Test
    @WithMockUser(authorities = "PERM_SETTINGS_MANAGE")
    void list_filtersByTriggerType() throws Exception {
        when(ruleRepository.findByTriggerTypeAndIsActiveTrueOrderByExecutionOrderAsc(
                AutomationTriggerType.STATUS_CHANGED)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/automation/rules")
                        .param("triggerType", "STATUS_CHANGED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ── GET /admin/automation/rules/meta/triggers ─────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_SETTINGS_MANAGE")
    void triggerTypes_returnsAllTriggers() throws Exception {
        mockMvc.perform(get("/api/admin/automation/rules/meta/triggers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.TICKET_CREATED").exists())
                .andExpect(jsonPath("$.STATUS_CHANGED").exists())
                .andExpect(jsonPath("$.SLA_BREACHED").exists());
    }

    // ── POST /admin/automation/rules ──────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_SETTINGS_MANAGE")
    void create_returns201_withSavedRule() throws Exception {
        AutomationRule saved = rule("New Rule", AutomationTriggerType.TICKET_CREATED);
        when(ruleRepository.save(any(AutomationRule.class))).thenReturn(saved);

        mockMvc.perform(post("/api/admin/automation/rules")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Rule\",\"triggerType\":\"TICKET_CREATED\",\"executionOrder\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Rule"))
                .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    @WithMockUser(authorities = "PERM_SETTINGS_MANAGE")
    void create_returns400_whenNameMissing() throws Exception {
        mockMvc.perform(post("/api/admin/automation/rules")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"triggerType\":\"TICKET_CREATED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "PERM_SETTINGS_MANAGE")
    void create_returns400_whenTriggerTypeMissing() throws Exception {
        mockMvc.perform(post("/api/admin/automation/rules")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"My Rule\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── PUT /admin/automation/rules/{id} ──────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_SETTINGS_MANAGE")
    void update_returns200_whenRuleExists() throws Exception {
        AutomationRule existing = rule("Old Name", AutomationTriggerType.TICKET_CREATED);
        AutomationRule updated = rule("Updated Rule", AutomationTriggerType.STATUS_CHANGED);
        when(ruleRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(ruleRepository.save(any())).thenReturn(updated);

        mockMvc.perform(put("/api/admin/automation/rules/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Rule\",\"triggerType\":\"STATUS_CHANGED\"," +
                                "\"isActive\":true,\"executionOrder\":5}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "PERM_SETTINGS_MANAGE")
    void update_returns404_whenRuleNotFound() throws Exception {
        when(ruleRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/admin/automation/rules/99")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"triggerType\":\"TICKET_CREATED\"," +
                                "\"isActive\":false,\"executionOrder\":0}"))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /admin/automation/rules/{id} ───────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_SETTINGS_MANAGE")
    void delete_returns204_whenRuleExists() throws Exception {
        when(ruleRepository.existsById(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/admin/automation/rules/1").with(csrf()))
                .andExpect(status().isNoContent());

        verify(ruleRepository).deleteById(1L);
    }

    @Test
    @WithMockUser(authorities = "PERM_SETTINGS_MANAGE")
    void delete_returns404_whenRuleNotFound() throws Exception {
        when(ruleRepository.existsById(99L)).thenReturn(false);

        mockMvc.perform(delete("/api/admin/automation/rules/99").with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AutomationRule rule(String name, AutomationTriggerType trigger) {
        AutomationRule r = new AutomationRule();
        r.setName(name);
        r.setTriggerType(trigger);
        r.setActive(false);
        r.setExecutionOrder(100);
        return r;
    }
}
