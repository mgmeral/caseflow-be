package com.caseflow.identity.api;

import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.exception.AdminLockoutException;
import com.caseflow.common.exception.RoleNotFoundException;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.identity.api.dto.CreateRoleRequest;
import com.caseflow.identity.api.dto.RoleResponse;
import com.caseflow.identity.api.dto.RoleSummaryResponse;
import com.caseflow.identity.api.dto.UpdateRoleRequest;
import com.caseflow.identity.api.mapper.RoleMapper;
import com.caseflow.identity.domain.Permission;
import com.caseflow.identity.domain.Role;
import com.caseflow.identity.domain.TicketScope;
import com.caseflow.identity.service.RoleService;
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
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoleController.class)
@Import(SecurityConfig.class)
class RoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtTokenService jwtTokenService;

    @MockBean
    private CaseFlowUserDetailsService userDetailsService;

    @MockBean
    private RoleService roleService;

    @MockBean
    private RoleMapper roleMapper;

    // ── GET /api/roles/permissions ────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_ROLE_MANAGE")
    void getPermissionCatalog_returns200_withAllPermissions() throws Exception {
        when(roleService.getPermissionCatalog()).thenReturn(List.of(Permission.values()));

        mockMvc.perform(get("/api/roles/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0]").value("USER_MANAGE"));
    }

    @Test
    @WithMockUser
    void getPermissionCatalog_returns403_withoutPermission() throws Exception {
        mockMvc.perform(get("/api/roles/permissions"))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/roles ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_ROLE_MANAGE")
    void listRoles_returns200_withSummaryList() throws Exception {
        Role role = new Role();
        RoleSummaryResponse summary = new RoleSummaryResponse(1L, "ADMIN", "Administrator", true, TicketScope.ALL, 14);

        when(roleService.findAll()).thenReturn(List.of(role));
        when(roleMapper.toSummaryResponse(role)).thenReturn(summary);

        mockMvc.perform(get("/api/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].code").value("ADMIN"))
                .andExpect(jsonPath("$[0].permissionCount").value(14));
    }

    @Test
    void listRoles_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/roles"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/roles/{id} ───────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_ROLE_MANAGE")
    void getById_returns200_withRoleResponse() throws Exception {
        Role role = new Role();
        RoleResponse response = makeRoleResponse(2L, "AGENT", Set.of("TICKET_READ", "INTERNAL_NOTE_ADD"));

        when(roleService.getById(2L)).thenReturn(role);
        when(roleMapper.toResponse(role)).thenReturn(response);

        mockMvc.perform(get("/api/roles/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.code").value("AGENT"))
                .andExpect(jsonPath("$.permissionCodes").isArray())
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @WithMockUser(authorities = "PERM_ROLE_MANAGE")
    void getById_returns404_whenRoleNotFound() throws Exception {
        when(roleService.getById(999L)).thenThrow(new RoleNotFoundException(999L));

        mockMvc.perform(get("/api/roles/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").exists());
    }

    // ── POST /api/roles ───────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_ROLE_MANAGE")
    void createRole_returns201_withValidRequest() throws Exception {
        CreateRoleRequest request = new CreateRoleRequest(
                "SUPERVISOR", "Supervisor", "Supervises agents",
                TicketScope.ALL,
                Set.of(Permission.TICKET_READ, Permission.INTERNAL_NOTE_ADD)
        );
        Role role = new Role();
        RoleResponse response = makeRoleResponse(5L, "SUPERVISOR", Set.of("TICKET_READ", "INTERNAL_NOTE_ADD"));

        when(roleService.createRole(any(CreateRoleRequest.class))).thenReturn(role);
        when(roleMapper.toResponse(role)).thenReturn(response);

        mockMvc.perform(post("/api/roles")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.code").value("SUPERVISOR"));
    }

    @Test
    @WithMockUser(authorities = "PERM_ROLE_MANAGE")
    void createRole_returns400_whenCodeIsBlank() throws Exception {
        CreateRoleRequest request = new CreateRoleRequest(
                "", "Supervisor", null, TicketScope.ALL, Set.of()
        );

        mockMvc.perform(post("/api/roles")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(authorities = "PERM_ROLE_MANAGE")
    void createRole_returns400_whenNameIsBlank() throws Exception {
        CreateRoleRequest request = new CreateRoleRequest(
                "SUPERVISOR", "", null, TicketScope.ALL, Set.of()
        );

        mockMvc.perform(post("/api/roles")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(authorities = "PERM_ROLE_MANAGE")
    void createRole_returns400_whenTicketScopeIsNull() throws Exception {
        CreateRoleRequest request = new CreateRoleRequest(
                "SUPERVISOR", "Supervisor", null, null, Set.of()
        );

        mockMvc.perform(post("/api/roles")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser
    void createRole_returns403_withoutPermission() throws Exception {
        CreateRoleRequest request = new CreateRoleRequest(
                "SUPERVISOR", "Supervisor", null, TicketScope.ALL, Set.of()
        );

        mockMvc.perform(post("/api/roles")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ── PUT /api/roles/{id} ───────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_ROLE_MANAGE")
    void updateRole_returns200_withUpdatedResponse() throws Exception {
        UpdateRoleRequest request = new UpdateRoleRequest(
                "AGENT_V2", "Agent v2", "Updated agent role",
                TicketScope.OWN_GROUPS,
                Set.of(Permission.TICKET_READ, Permission.INTERNAL_NOTE_ADD, Permission.CUSTOMER_REPLY_SEND)
        );
        Role role = new Role();
        RoleResponse response = makeRoleResponse(3L, "AGENT_V2",
                Set.of("TICKET_READ", "INTERNAL_NOTE_ADD", "CUSTOMER_REPLY_SEND"));

        when(roleService.updateRole(eq(3L), any(UpdateRoleRequest.class))).thenReturn(role);
        when(roleMapper.toResponse(role)).thenReturn(response);

        mockMvc.perform(put("/api/roles/3")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AGENT_V2"));
    }

    // ── PATCH /api/roles/{id}/activate & deactivate ───────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_ROLE_MANAGE")
    void activate_returns204() throws Exception {
        mockMvc.perform(patch("/api/roles/4/activate").with(csrf()))
                .andExpect(status().isNoContent());
        verify(roleService).activate(4L);
    }

    @Test
    @WithMockUser(authorities = "PERM_ROLE_MANAGE")
    void deactivate_returns204() throws Exception {
        mockMvc.perform(patch("/api/roles/4/deactivate").with(csrf()))
                .andExpect(status().isNoContent());
        verify(roleService).deactivate(4L);
    }

    @Test
    @WithMockUser(authorities = "PERM_ROLE_MANAGE")
    void deactivate_returns409_whenAdminLockoutWouldOccur() throws Exception {
        doThrow(new AdminLockoutException("Cannot deactivate: no remaining admin-capable users"))
                .when(roleService).deactivate(1L);

        mockMvc.perform(patch("/api/roles/1/deactivate").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN_LOCKOUT"));
    }

    // ── DELETE /api/roles/{id} ────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_ROLE_MANAGE")
    void deleteRole_returns204() throws Exception {
        mockMvc.perform(delete("/api/roles/9").with(csrf()))
                .andExpect(status().isNoContent());
        verify(roleService).deleteRole(9L);
    }

    @Test
    @WithMockUser(authorities = "PERM_ROLE_MANAGE")
    void deleteRole_returns409_whenAdminLockoutWouldOccur() throws Exception {
        doThrow(new AdminLockoutException("Cannot delete: no remaining admin-capable users"))
                .when(roleService).deleteRole(1L);

        mockMvc.perform(delete("/api/roles/1").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN_LOCKOUT"));
    }

    @Test
    @WithMockUser(authorities = "PERM_ROLE_MANAGE")
    void deleteRole_returns404_whenRoleNotFound() throws Exception {
        doThrow(new RoleNotFoundException(99L)).when(roleService).deleteRole(99L);

        mockMvc.perform(delete("/api/roles/99").with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RoleResponse makeRoleResponse(Long id, String code, Set<String> permissionCodes) {
        return new RoleResponse(id, code, code + " Role", null, true,
                TicketScope.ALL, permissionCodes, 0, Instant.now(), Instant.now());
    }
}
