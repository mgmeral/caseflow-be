package com.caseflow.email.api;

import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.email.domain.IngressEventStatus;
import com.caseflow.email.service.IngressEventAdminService;
import com.caseflow.ticket.security.TicketAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IngressEventAdminController.class)
@Import(SecurityConfig.class)
class IngressEventAdminControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private JwtTokenService jwtTokenService;
    @MockBean private CaseFlowUserDetailsService userDetailsService;
    @MockBean private IngressEventAdminService adminService;
    @MockBean(name = "ticketAuth") private TicketAuthorizationService ticketAuth;

    // ── GET /api/admin/ingress-events ─────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_CONFIG_VIEW")
    void list_returns200_withPageOfEvents() throws Exception {
        when(adminService.findFiltered(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/admin/ingress-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_CONFIG_VIEW")
    void list_withStatusFilter_passesFilterToService() throws Exception {
        when(adminService.findFiltered(
                any(IngressEventStatus.class), isNull(), isNull(), isNull(), isNull(), isNull(),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/admin/ingress-events")
                        .param("status", "FAILED"))
                .andExpect(status().isOk());

        verify(adminService).findFiltered(
                any(IngressEventStatus.class), isNull(), isNull(), isNull(), isNull(), isNull(),
                any(Pageable.class));
    }

    @Test
    void list_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/admin/ingress-events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void list_returns403_whenMissingEmailConfigView() throws Exception {
        mockMvc.perform(get("/api/admin/ingress-events"))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/admin/ingress-events/{id}/process ───────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_CONFIG_MANAGE")
    void process_returns204() throws Exception {
        doNothing().when(adminService).processEvent(1L);

        mockMvc.perform(post("/api/admin/ingress-events/1/process").with(csrf()))
                .andExpect(status().isNoContent());

        verify(adminService).processEvent(1L);
    }

    @Test
    void process_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/admin/ingress-events/1/process").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_CONFIG_VIEW")
    void process_returns403_whenMissingEmailConfigManage() throws Exception {
        mockMvc.perform(post("/api/admin/ingress-events/1/process").with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/admin/ingress-events/{id}/retry ─────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_CONFIG_MANAGE")
    void retry_returns204() throws Exception {
        doNothing().when(adminService).retryEvent(2L);

        mockMvc.perform(post("/api/admin/ingress-events/2/retry").with(csrf()))
                .andExpect(status().isNoContent());

        verify(adminService).retryEvent(2L);
    }

    // ── POST /api/admin/ingress-events/{id}/quarantine ────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_CONFIG_MANAGE")
    void quarantine_returns204_withReason() throws Exception {
        doNothing().when(adminService).quarantineEvent(3L, "spam");

        mockMvc.perform(post("/api/admin/ingress-events/3/quarantine")
                        .with(csrf())
                        .param("reason", "spam"))
                .andExpect(status().isNoContent());

        verify(adminService).quarantineEvent(3L, "spam");
    }

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_CONFIG_MANAGE")
    void quarantine_returns204_withoutReason() throws Exception {
        doNothing().when(adminService).quarantineEvent(3L, null);

        mockMvc.perform(post("/api/admin/ingress-events/3/quarantine").with(csrf()))
                .andExpect(status().isNoContent());
    }

    // ── POST /api/admin/ingress-events/{id}/release ───────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_CONFIG_MANAGE")
    void release_returns204() throws Exception {
        doNothing().when(adminService).releaseEvent(4L);

        mockMvc.perform(post("/api/admin/ingress-events/4/release").with(csrf()))
                .andExpect(status().isNoContent());

        verify(adminService).releaseEvent(4L);
    }

    @Test
    void release_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/admin/ingress-events/4/release").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_CONFIG_VIEW")
    void release_returns403_whenMissingEmailConfigManage() throws Exception {
        mockMvc.perform(post("/api/admin/ingress-events/4/release").with(csrf()))
                .andExpect(status().isForbidden());
    }

}
