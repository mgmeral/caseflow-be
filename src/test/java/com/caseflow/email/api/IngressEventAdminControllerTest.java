package com.caseflow.email.api;

import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.email.domain.EmailIngressEvent;
import com.caseflow.email.domain.IngressEventStatus;
import com.caseflow.email.service.IngressEventAdminService;
import com.caseflow.email.service.IngressEventFilter;
import com.caseflow.security.audit.SecurityAuditService;
import com.caseflow.ticket.security.TicketAuthorizationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @MockBean private SecurityAuditService auditService;
    @MockBean(name = "ticketAuth") private TicketAuthorizationService ticketAuth;

    // ── GET /api/admin/ingress-events ─────────────────────────────────────────

    private static EmailIngressEvent event() {
        EmailIngressEvent e = new EmailIngressEvent();
        ReflectionTestUtils.setField(e, "id", 7L);
        e.setMessageId("<abc@mail.acme.com>");
        e.setRawFrom("ali.veli@acme.com");
        e.setRawSubject("Order 12345678 for TKT-0000123");
        e.setStatus(IngressEventStatus.QUARANTINED);
        e.setFailureReason("Unknown sender: ali.veli@acme.com");
        e.setReceivedAt(Instant.parse("2026-09-27T10:00:00Z"));
        return e;
    }

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_CONFIG_VIEW")
    void list_returns200_withPageOfEvents() throws Exception {
        when(adminService.findFiltered(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/admin/ingress-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_CONFIG_VIEW")
    void list_passesStatusesAndSearch_andSortsNewestFirst() throws Exception {
        when(adminService.findFiltered(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/admin/ingress-events")
                        .param("status", "FAILED", "QUARANTINED")
                        .param("q", "  acme  "))
                .andExpect(status().isOk());

        ArgumentCaptor<IngressEventFilter> filter = ArgumentCaptor.forClass(IngressEventFilter.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(adminService).findFiltered(filter.capture(), pageable.capture());
        assertThat(filter.getValue().statuses())
                .containsExactly(IngressEventStatus.FAILED, IngressEventStatus.QUARANTINED);
        assertThat(filter.getValue().q()).isEqualTo("acme");
        assertThat(pageable.getValue().getSort().getOrderFor("receivedAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    @WithMockUser(authorities = {"PERM_EMAIL_CONFIG_VIEW", "PERM_EMAIL_CONFIG_MANAGE"})
    void list_isMasked_evenForManagers() throws Exception {
        when(adminService.findFiltered(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(event())));

        mockMvc.perform(get("/api/admin/ingress-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].rawFrom").value("a***@acme.com"))
                .andExpect(jsonPath("$.items[0].rawSubject").value("Order *** for TKT-0000123"))
                .andExpect(jsonPath("$.items[0].failureReason").value("Unknown sender: a***@acme.com"))
                .andExpect(jsonPath("$.items[0].masked").value(true));
        verifyNoInteractions(auditService);
    }

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_CONFIG_VIEW")
    void counts_returnsEveryStatus_underNonStatusFilters() throws Exception {
        Map<IngressEventStatus, Long> counts = new EnumMap<>(IngressEventStatus.class);
        for (IngressEventStatus s : IngressEventStatus.values()) counts.put(s, 0L);
        counts.put(IngressEventStatus.FAILED, 3L);
        when(adminService.countByStatus(any())).thenReturn(counts);

        mockMvc.perform(get("/api/admin/ingress-events/counts").param("mailboxId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.FAILED").value(3))
                .andExpect(jsonPath("$.PROCESSED").value(0));

        ArgumentCaptor<IngressEventFilter> filter = ArgumentCaptor.forClass(IngressEventFilter.class);
        verify(adminService).countByStatus(filter.capture());
        assertThat(filter.getValue().mailboxId()).isEqualTo(2L);
        assertThat(filter.getValue().statuses()).isEmpty();
    }

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_CONFIG_VIEW")
    void detail_isMasked_withoutManage_andNotAudited() throws Exception {
        when(adminService.getById(7L)).thenReturn(event());

        mockMvc.perform(get("/api/admin/ingress-events/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawFrom").value("a***@acme.com"))
                .andExpect(jsonPath("$.masked").value(true));
        verifyNoInteractions(auditService);
    }

    @Test
    @WithMockUser(username = "alice", authorities = {"PERM_EMAIL_CONFIG_VIEW", "PERM_EMAIL_CONFIG_MANAGE"})
    void detail_isFull_withManage_andAudited() throws Exception {
        when(adminService.getById(7L)).thenReturn(event());

        mockMvc.perform(get("/api/admin/ingress-events/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawFrom").value("ali.veli@acme.com"))
                .andExpect(jsonPath("$.rawSubject").value("Order 12345678 for TKT-0000123"))
                .andExpect(jsonPath("$.masked").value(false));
        verify(auditService).recordSensitiveDataView(isNull(), eq("alice"), any(), eq("INGRESS_EVENT:7"));
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
