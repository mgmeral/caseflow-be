package com.caseflow.auth.api;

import com.caseflow.auth.AuthService;
import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.auth.api.dto.LoginRequest;
import com.caseflow.auth.api.dto.RefreshTokenRequest;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.identity.domain.Permission;
import com.caseflow.ticket.security.TicketAuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private JwtTokenService jwtTokenService;
    @MockBean private CaseFlowUserDetailsService userDetailsService;
    @MockBean private AuthService authService;
    @MockBean(name = "ticketAuth") private TicketAuthorizationService ticketAuth;

    // ── POST /api/auth/login ──────────────────────────────────────────────────

    @Test
    void login_returns200_withTokens() throws Exception {
        when(authService.login("alice", "pass123"))
                .thenReturn(new AuthService.TokenPair("access-tok", "refresh-tok", 3600));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("alice", "pass123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-tok"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-tok"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    // ── POST /api/auth/refresh ────────────────────────────────────────────────

    @Test
    void refresh_returns200() throws Exception {
        when(authService.refresh(anyString()))
                .thenReturn(new AuthService.TokenPair("new-access", "new-refresh", 3600));

        mockMvc.perform(post("/api/auth/refresh")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest("old-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"));
    }

    // ── POST /api/auth/logout ─────────────────────────────────────────────────

    @Test
    void logout_returns204() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest("tok"))))
                .andExpect(status().isNoContent());
    }

    // ── GET /api/auth/me ──────────────────────────────────────────────────────

    @Test
    void me_returns200_withFullContractShape() throws Exception {
        CaseFlowUserDetails principal = mockPrincipal();

        mockMvc.perform(get("/api/auth/me").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@caseflow.dev"))
                .andExpect(jsonPath("$.fullName").value("Alice Admin"))
                .andExpect(jsonPath("$.roleId").value(1))
                .andExpect(jsonPath("$.roleCode").value("ADMIN"))
                .andExpect(jsonPath("$.roleName").value("Admin"))
                .andExpect(jsonPath("$.permissionCodes").isArray())
                .andExpect(jsonPath("$.permissionCodes[0]").value("EMAIL_CONFIG_VIEW"))
                .andExpect(jsonPath("$.permissionCodes[1]").value("TICKET_READ"))
                .andExpect(jsonPath("$.ticketScope").value("ALL"))
                .andExpect(jsonPath("$.groupIds").isArray())
                .andExpect(jsonPath("$.groupIds[0]").value(3));
    }

    @Test
    void me_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CaseFlowUserDetails mockPrincipal() {
        CaseFlowUserDetails principal = mock(CaseFlowUserDetails.class);
        when(principal.getUserId()).thenReturn(42L);
        when(principal.getUsername()).thenReturn("alice");
        when(principal.getEmail()).thenReturn("alice@caseflow.dev");
        when(principal.getFullName()).thenReturn("Alice Admin");
        when(principal.getRoleId()).thenReturn(1L);
        when(principal.getRoleCode()).thenReturn("ADMIN");
        when(principal.getRoleName()).thenReturn("Admin");
        // permissionCodes are sorted in the controller: Permission.name() sorted alphabetically
        when(principal.getPermissions()).thenReturn(
                Set.of(Permission.TICKET_READ, Permission.EMAIL_CONFIG_VIEW));
        when(principal.getTicketScope()).thenReturn("ALL");
        when(principal.getGroupIds()).thenReturn(List.of(3L));
        when(principal.getPassword()).thenReturn("");
        when(principal.isEnabled()).thenReturn(true);
        when(principal.isAccountNonExpired()).thenReturn(true);
        when(principal.isAccountNonLocked()).thenReturn(true);
        when(principal.isCredentialsNonExpired()).thenReturn(true);
        when(principal.getAuthorities()).thenReturn(List.of());
        return principal;
    }
}
