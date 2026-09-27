package com.caseflow.sla.api;

import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.exception.SlaPolicyNotFoundException;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.identity.domain.Permission;
import com.caseflow.sla.api.dto.SlaPolicyRequest;
import com.caseflow.sla.api.dto.SlaPolicyResponse;
import com.caseflow.sla.domain.SlaScope;
import com.caseflow.sla.service.SlaBackfillService;
import com.caseflow.sla.service.SlaService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression coverage for BUG-001 — {@code SlaPolicyController} previously gated every
 * endpoint on {@code PERM_SETTINGS_MANAGE}, an authority no role could ever hold since it
 * did not correspond to any {@link Permission} enum constant, making all 6 endpoints
 * unreachable by any role. No test file existed for this controller before this fix.
 */
@WebMvcTest(SlaPolicyController.class)
@Import(SecurityConfig.class)
class SlaPolicyControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private SlaService slaService;
    @MockBean private SlaBackfillService slaBackfillService;
    @MockBean private JwtTokenService jwtTokenService;
    @MockBean private CaseFlowUserDetailsService userDetailsService;

    /**
     * Applies the real {@link Permission#ADMIN_CONFIG} authority, derived from the actual
     * enum rather than a hardcoded string — see the equivalent helper (and its rationale)
     * in {@code AutomationRuleControllerTest}.
     */
    private static MockHttpServletRequestBuilder withAdminConfig(MockHttpServletRequestBuilder builder) {
        return builder.with(user("agent").authorities(
                new SimpleGrantedAuthority("PERM_" + Permission.ADMIN_CONFIG.name())));
    }

    private static SlaPolicyResponse policy(Long id) {
        return new SlaPolicyResponse(id, "Default Global", SlaScope.GLOBAL, null, null,
                60, 480, 30, true, Instant.now(), Instant.now());
    }

    private static String validRequestJson() {
        return "{\"name\":\"Default Global\",\"scope\":\"GLOBAL\","
                + "\"firstResponseTargetMinutes\":60,\"resolutionTargetMinutes\":480,"
                + "\"warningBeforeBreachMinutes\":30,\"isActive\":true}";
    }

    // ── GET /api/admin/sla/policies ───────────────────────────────────────────

    @Test
    void list_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/admin/sla/policies"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void list_returns403_whenMissingPermission() throws Exception {
        mockMvc.perform(get("/api/admin/sla/policies"))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_returns200_withPolicies() throws Exception {
        when(slaService.findAll()).thenReturn(List.of(policy(1L)));

        mockMvc.perform(withAdminConfig(get("/api/admin/sla/policies")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Default Global"))
                .andExpect(jsonPath("$[0].scope").value("GLOBAL"));
    }

    // ── GET /api/admin/sla/policies/{id} ──────────────────────────────────────

    @Test
    void get_returns200_whenFound() throws Exception {
        when(slaService.findById(1L)).thenReturn(policy(1L));

        mockMvc.perform(withAdminConfig(get("/api/admin/sla/policies/1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void get_returns404_whenNotFound() throws Exception {
        when(slaService.findById(99L)).thenThrow(new SlaPolicyNotFoundException(99L));

        mockMvc.perform(withAdminConfig(get("/api/admin/sla/policies/99")))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/admin/sla/policies ──────────────────────────────────────────

    @Test
    @WithMockUser
    void create_returns403_whenMissingPermission() throws Exception {
        mockMvc.perform(post("/api/admin/sla/policies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_returns201_withSavedPolicy() throws Exception {
        when(slaService.create(any(SlaPolicyRequest.class))).thenReturn(policy(1L));

        mockMvc.perform(withAdminConfig(post("/api/admin/sla/policies"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Default Global"));
    }

    @Test
    void create_returns400_whenNameMissing() throws Exception {
        mockMvc.perform(withAdminConfig(post("/api/admin/sla/policies"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"GLOBAL\",\"firstResponseTargetMinutes\":60,"
                                + "\"resolutionTargetMinutes\":480,\"warningBeforeBreachMinutes\":30}"))
                .andExpect(status().isBadRequest());
    }

    // ── PUT /api/admin/sla/policies/{id} ──────────────────────────────────────

    @Test
    void update_returns200_whenFound() throws Exception {
        when(slaService.update(eq(1L), any(SlaPolicyRequest.class))).thenReturn(policy(1L));

        mockMvc.perform(withAdminConfig(put("/api/admin/sla/policies/1"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isOk());
    }

    @Test
    void update_returns404_whenNotFound() throws Exception {
        when(slaService.update(eq(99L), any(SlaPolicyRequest.class)))
                .thenThrow(new SlaPolicyNotFoundException(99L));

        mockMvc.perform(withAdminConfig(put("/api/admin/sla/policies/99"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /api/admin/sla/policies/{id} ───────────────────────────────────

    @Test
    void delete_returns204_whenFound() throws Exception {
        mockMvc.perform(withAdminConfig(delete("/api/admin/sla/policies/1")).with(csrf()))
                .andExpect(status().isNoContent());

        verify(slaService).delete(1L);
    }

    @Test
    void delete_returns404_whenNotFound() throws Exception {
        org.mockito.Mockito.doThrow(new SlaPolicyNotFoundException(99L))
                .when(slaService).delete(99L);

        mockMvc.perform(withAdminConfig(delete("/api/admin/sla/policies/99")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/admin/sla/policies/backfill ─────────────────────────────────

    @Test
    void backfill_returns200_withSummary() throws Exception {
        when(slaBackfillService.backfillMissingDueDates())
                .thenReturn(new SlaBackfillService.BackfillResult(10, 7, 2, 1));

        mockMvc.perform(withAdminConfig(post("/api/admin/sla/policies/backfill")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEligible").value(10))
                .andExpect(jsonPath("$.stamped").value(7))
                .andExpect(jsonPath("$.skipped").value(2))
                .andExpect(jsonPath("$.failed").value(1));
    }

    @Test
    @WithMockUser
    void backfill_returns403_whenMissingPermission() throws Exception {
        mockMvc.perform(post("/api/admin/sla/policies/backfill").with(csrf()))
                .andExpect(status().isForbidden());
    }
}
