package com.caseflow.email.api;

import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.email.api.dto.MailboxRequest;
import com.caseflow.email.api.dto.MailboxResponse;
import com.caseflow.email.api.mapper.EmailMailboxMapper;
import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.domain.InboundMode;
import com.caseflow.email.domain.OutboundMode;
import com.caseflow.email.domain.ProviderType;
import com.caseflow.email.service.EmailMailboxService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MailboxController.class)
@Import(SecurityConfig.class)
class MailboxControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private JwtTokenService jwtTokenService;
    @MockBean private CaseFlowUserDetailsService userDetailsService;
    @MockBean private EmailMailboxService mailboxService;
    @MockBean private EmailMailboxMapper mailboxMapper;
    @MockBean(name = "ticketAuth") private TicketAuthorizationService ticketAuth;

    // ── POST /api/admin/mailboxes ─────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_CONFIG_MANAGE")
    void create_returns201_withMailboxResponse() throws Exception {
        MailboxRequest request = makeRequest();
        MailboxResponse response = makeResponse(1L, "support@caseflow.dev");

        when(mailboxMapper.toEntity(any())).thenReturn(new EmailMailbox());
        when(mailboxService.create(any())).thenReturn(new EmailMailbox());
        when(mailboxMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(post("/api/admin/mailboxes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.address").value("support@caseflow.dev"));
    }

    @Test
    void create_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/admin/mailboxes").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void create_returns403_whenMissingEmailConfigManage() throws Exception {
        mockMvc.perform(post("/api/admin/mailboxes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeRequest())))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/admin/mailboxes ──────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_CONFIG_VIEW")
    void list_returns200_withMailboxList() throws Exception {
        List<MailboxResponse> responses = List.of(
                makeResponse(1L, "support@caseflow.dev"),
                makeResponse(2L, "noreply@caseflow.dev")
        );
        when(mailboxService.findAll()).thenReturn(List.of());
        when(mailboxMapper.toResponseList(any())).thenReturn(responses);

        mockMvc.perform(get("/api/admin/mailboxes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].address").value("noreply@caseflow.dev"));
    }

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_CONFIG_VIEW")
    void getById_returns200() throws Exception {
        MailboxResponse response = makeResponse(1L, "support@caseflow.dev");
        when(mailboxService.getById(1L)).thenReturn(new EmailMailbox());
        when(mailboxMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(get("/api/admin/mailboxes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    // ── PUT /api/admin/mailboxes/{id} ─────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_CONFIG_MANAGE")
    void update_returns200() throws Exception {
        MailboxResponse response = makeResponse(1L, "support@caseflow.dev");

        when(mailboxMapper.toEntity(any())).thenReturn(new EmailMailbox());
        when(mailboxService.update(anyLong(), any())).thenReturn(new EmailMailbox());
        when(mailboxMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(put("/api/admin/mailboxes/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(makeRequest())))
                .andExpect(status().isOk());
    }

    // ── PATCH /api/admin/mailboxes/{id}/activate ──────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_CONFIG_MANAGE")
    void activate_returns200() throws Exception {
        MailboxResponse response = makeResponse(1L, "support@caseflow.dev");
        when(mailboxService.activate(1L)).thenReturn(new EmailMailbox());
        when(mailboxMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(patch("/api/admin/mailboxes/1/activate").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_CONFIG_MANAGE")
    void deactivate_returns200() throws Exception {
        MailboxResponse response = makeResponse(1L, "support@caseflow.dev");
        when(mailboxService.deactivate(1L)).thenReturn(new EmailMailbox());
        when(mailboxMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(patch("/api/admin/mailboxes/1/deactivate").with(csrf()))
                .andExpect(status().isOk());
    }

    // ── DELETE /api/admin/mailboxes/{id} ──────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_EMAIL_CONFIG_MANAGE")
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/admin/mailboxes/1").with(csrf()))
                .andExpect(status().isNoContent());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MailboxRequest makeRequest() {
        return new MailboxRequest(
                "Support Inbox", null, "support@caseflow.dev",
                ProviderType.SMTP_RELAY, InboundMode.WEBHOOK, OutboundMode.SMTP,
                true, null, null, null, null, null, null, null);
    }

    private MailboxResponse makeResponse(Long id, String address) {
        return new MailboxResponse(id, "Support Inbox", null, address,
                ProviderType.SMTP_RELAY, InboundMode.WEBHOOK, OutboundMode.SMTP,
                true, null, null, null, null, null, false, null, null,
                Instant.now(), Instant.now());
    }
}
