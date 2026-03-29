package com.caseflow.workflow.transfer.api;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.ticket.security.TicketAuthorizationService;
import com.caseflow.workflow.transfer.TransferService;
import com.caseflow.workflow.transfer.dto.TransferResponse;
import com.caseflow.workflow.transfer.dto.TransferSummaryResponse;
import com.caseflow.workflow.transfer.dto.TransferTicketRequest;
import com.caseflow.workflow.transfer.mapper.TransferMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import com.caseflow.workflow.domain.Transfer;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferController.class)
@Import(SecurityConfig.class)
class TransferControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private JwtTokenService jwtTokenService;
    @MockBean private CaseFlowUserDetailsService userDetailsService;
    @MockBean private TransferService transferService;
    @MockBean private TransferMapper transferMapper;
    @MockBean(name = "ticketAuth") private TicketAuthorizationService ticketAuth;

    // ── POST /api/transfers ───────────────────────────────────────────────────

    @Test
    void transfer_returns201_withTransferResponse() throws Exception {
        CaseFlowUserDetails principal = mockPrincipal(1L);
        when(ticketAuth.canTransferTicket(any(Authentication.class), anyLong(), anyLong()))
                .thenReturn(true);

        TransferResponse response = new TransferResponse(10L, 5L, 1L, 2L, 1L, Instant.now(), "Escalating");
        when(transferService.transfer(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyBoolean()))
                .thenReturn(new Transfer());
        when(transferMapper.toResponse(any())).thenReturn(response);

        TransferTicketRequest request = new TransferTicketRequest(5L, 1L, 2L, "Escalating", false);

        mockMvc.perform(post("/api/transfers")
                        .with(csrf())
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.ticketId").value(5))
                .andExpect(jsonPath("$.fromGroupId").value(1))
                .andExpect(jsonPath("$.toGroupId").value(2))
                .andExpect(jsonPath("$.reason").value("Escalating"));
    }

    @Test
    void transfer_returns403_whenNotAuthorized() throws Exception {
        CaseFlowUserDetails principal = mockPrincipal(1L);
        when(ticketAuth.canTransferTicket(any(), anyLong(), anyLong())).thenReturn(false);

        TransferTicketRequest request = new TransferTicketRequest(5L, 1L, 2L, null, false);

        mockMvc.perform(post("/api/transfers")
                        .with(csrf())
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void transfer_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/transfers").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void transfer_returns400_whenMissingRequiredField() throws Exception {
        CaseFlowUserDetails principal = mockPrincipal(1L);
        // ticketId is null — @NotNull violation
        String invalidJson = "{\"fromGroupId\":1,\"toGroupId\":2}";

        mockMvc.perform(post("/api/transfers")
                        .with(csrf())
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ── GET /api/transfers/by-ticket/{ticketId} ───────────────────────────────

    @Test
    void getTransferHistory_returns200_withList() throws Exception {
        CaseFlowUserDetails principal = mockPrincipal(1L);
        when(ticketAuth.canReadTicket(any(Authentication.class), anyLong())).thenReturn(true);

        TransferSummaryResponse summary = new TransferSummaryResponse(10L, 5L, 1L, 2L, Instant.now());
        when(transferService.getTransferHistory(5L)).thenReturn(List.of(new Transfer()));
        when(transferMapper.toSummaryResponseList(any())).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/transfers/by-ticket/5")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].ticketId").value(5))
                .andExpect(jsonPath("$[0].fromGroupId").value(1))
                .andExpect(jsonPath("$[0].toGroupId").value(2));
    }

    @Test
    void getTransferHistory_returns403_whenNotAuthorized() throws Exception {
        CaseFlowUserDetails principal = mockPrincipal(1L);
        when(ticketAuth.canReadTicket(any(Authentication.class), anyLong())).thenReturn(false);

        mockMvc.perform(get("/api/transfers/by-ticket/5")
                        .with(user(principal)))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CaseFlowUserDetails mockPrincipal(Long userId) {
        CaseFlowUserDetails principal = mock(CaseFlowUserDetails.class);
        when(principal.getUserId()).thenReturn(userId);
        when(principal.getUsername()).thenReturn("testuser");
        when(principal.getPassword()).thenReturn("");
        when(principal.isEnabled()).thenReturn(true);
        when(principal.isAccountNonExpired()).thenReturn(true);
        when(principal.isAccountNonLocked()).thenReturn(true);
        when(principal.isCredentialsNonExpired()).thenReturn(true);
        when(principal.getAuthorities()).thenReturn(List.of());
        return principal;
    }
}
