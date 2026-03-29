package com.caseflow.customer.api;

import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.domain.UnknownSenderPolicy;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.customer.api.dto.CustomerEmailSettingsRequest;
import com.caseflow.customer.api.dto.CustomerEmailSettingsResponse;
import com.caseflow.customer.api.dto.RoutingRuleRequest;
import com.caseflow.customer.api.dto.RoutingRuleResponse;
import com.caseflow.customer.api.mapper.CustomerEmailSettingsMapper;
import com.caseflow.customer.domain.CustomerEmailSettings;
import com.caseflow.customer.domain.MatchingStrategy;
import com.caseflow.customer.domain.SenderMatchType;
import com.caseflow.customer.service.CustomerEmailSettingsService;
import com.caseflow.ticket.security.TicketAuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerEmailSettingsController.class)
@Import(SecurityConfig.class)
class CustomerEmailSettingsControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private JwtTokenService jwtTokenService;
    @MockBean private CaseFlowUserDetailsService userDetailsService;
    @MockBean private CustomerEmailSettingsService settingsService;
    @MockBean private CustomerEmailSettingsMapper mapper;
    @MockBean(name = "ticketAuth") private TicketAuthorizationService ticketAuth;

    // ── GET /api/customers/{id}/email-settings ────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_CONFIG_VIEW")
    void get_returns200_whenSettingsExist() throws Exception {
        CustomerEmailSettings settings = new CustomerEmailSettings();
        when(settingsService.findByCustomerId(1L)).thenReturn(Optional.of(settings));
        when(settingsService.findAllRules(1L)).thenReturn(List.of());
        when(mapper.toResponse(any(), any())).thenReturn(makeSettingsResponse(1L));

        mockMvc.perform(get("/api/customers/1/email-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(1));
    }

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_CONFIG_VIEW")
    void get_returns404_whenNoSettings() throws Exception {
        when(settingsService.findByCustomerId(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/customers/99/email-settings"))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/customers/1/email-settings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void get_returns403_whenMissingPermission() throws Exception {
        mockMvc.perform(get("/api/customers/1/email-settings"))
                .andExpect(status().isForbidden());
    }

    // ── PUT /api/customers/{id}/email-settings ────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_CONFIG_MANAGE")
    void upsert_returns200() throws Exception {
        CustomerEmailSettingsRequest request = new CustomerEmailSettingsRequest(
                UnknownSenderPolicy.MANUAL_REVIEW, MatchingStrategy.CONTACT_FIRST,
                true, false, false, false, null, null);
        CustomerEmailSettings settings = new CustomerEmailSettings();
        when(mapper.toEntity(any())).thenReturn(settings);
        when(settingsService.upsert(anyLong(), any())).thenReturn(settings);
        when(settingsService.findAllRules(anyLong())).thenReturn(List.of());
        when(mapper.toResponse(any(), any())).thenReturn(makeSettingsResponse(1L));

        mockMvc.perform(put("/api/customers/1/email-settings")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(1));
    }

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_CONFIG_VIEW")
    void upsert_returns403_withReadOnlyPermission() throws Exception {
        CustomerEmailSettingsRequest request = new CustomerEmailSettingsRequest(
                UnknownSenderPolicy.MANUAL_REVIEW, MatchingStrategy.CONTACT_FIRST,
                true, false, false, false, null, null);

        mockMvc.perform(put("/api/customers/1/email-settings")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/customers/{id}/email-settings/rules ─────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_CONFIG_MANAGE")
    void addRule_returns201() throws Exception {
        RoutingRuleRequest request = new RoutingRuleRequest(
                SenderMatchType.EXACT_EMAIL, "support@bigcorp.com", 10, true);
        RoutingRuleResponse response = makeRuleResponse(1L, 1L);
        when(mapper.toRuleEntity(any())).thenReturn(new com.caseflow.customer.domain.CustomerEmailRoutingRule());
        when(settingsService.addRule(anyLong(), any())).thenReturn(new com.caseflow.customer.domain.CustomerEmailRoutingRule());
        when(mapper.toRuleResponse(any())).thenReturn(response);

        mockMvc.perform(post("/api/customers/1/email-settings/rules")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    // ── PUT /api/customers/{id}/email-settings/rules/{ruleId} ─────────────────

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_CONFIG_MANAGE")
    void updateRule_returns200() throws Exception {
        RoutingRuleRequest request = new RoutingRuleRequest(
                SenderMatchType.DOMAIN, "bigcorp.com", 5, true);
        RoutingRuleResponse response = makeRuleResponse(1L, 1L);
        when(mapper.toRuleEntity(any())).thenReturn(new com.caseflow.customer.domain.CustomerEmailRoutingRule());
        when(settingsService.updateRule(anyLong(), anyLong(), any()))
                .thenReturn(new com.caseflow.customer.domain.CustomerEmailRoutingRule());
        when(mapper.toRuleResponse(any())).thenReturn(response);

        mockMvc.perform(put("/api/customers/1/email-settings/rules/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    // ── DELETE /api/customers/{id}/email-settings/rules/{ruleId} ─────────────

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_CONFIG_MANAGE")
    void deleteRule_returns204() throws Exception {
        mockMvc.perform(delete("/api/customers/1/email-settings/rules/1").with(csrf()))
                .andExpect(status().isNoContent());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CustomerEmailSettingsResponse makeSettingsResponse(Long customerId) {
        return new CustomerEmailSettingsResponse(
                1L, customerId,
                UnknownSenderPolicy.MANUAL_REVIEW, MatchingStrategy.CONTACT_FIRST,
                true, false, false, false, null, null,
                Instant.now(), List.of());
    }

    private RoutingRuleResponse makeRuleResponse(Long id, Long customerId) {
        return new RoutingRuleResponse(
                id, customerId, SenderMatchType.EXACT_EMAIL,
                "support@bigcorp.com", 10, true, Instant.now());
    }
}
