package com.caseflow.identity.api;

import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.exception.UserNotFoundException;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.identity.api.dto.CreateUserRequest;
import com.caseflow.identity.api.dto.UpdateUserRequest;
import com.caseflow.identity.api.dto.UserResponse;
import com.caseflow.identity.api.dto.UserSummaryResponse;
import com.caseflow.identity.api.mapper.UserMapper;
import com.caseflow.identity.domain.User;
import com.caseflow.identity.service.UserService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtTokenService jwtTokenService;

    @MockBean
    private CaseFlowUserDetailsService userDetailsService;

    @MockBean
    private UserService userService;

    @MockBean
    private UserMapper userMapper;

    // ── POST /api/users ───────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_USER_MANAGE")
    void createUser_returns201_withValidRequest() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "jsmith", "j.smith@example.com", "John Smith",
                "password123", 2L, List.of(1L, 2L), true
        );
        User user = new User();
        UserResponse response = makeUserResponse(10L, "jsmith", 2L, "AGENT");

        when(userService.createUser(any(CreateUserRequest.class))).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(response);

        mockMvc.perform(post("/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.username").value("jsmith"))
                .andExpect(jsonPath("$.roleCode").value("AGENT"));
    }

    @Test
    @WithMockUser(authorities = "PERM_USER_MANAGE")
    void createUser_returns400_whenUsernameIsBlank() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "", "j.smith@example.com", "John Smith",
                "password123", 2L, null, null
        );

        mockMvc.perform(post("/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(authorities = "PERM_USER_MANAGE")
    void createUser_returns400_whenPasswordTooShort() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "jsmith", "j.smith@example.com", "John Smith",
                "short", 2L, null, null
        );

        mockMvc.perform(post("/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(authorities = "PERM_USER_MANAGE")
    void createUser_returns400_whenRoleIdIsNull() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "jsmith", "j.smith@example.com", "John Smith",
                "password123", null, null, null
        );

        mockMvc.perform(post("/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser
    void createUser_returns403_withoutPermission() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "jsmith", "j.smith@example.com", "John Smith",
                "password123", 2L, null, null
        );

        mockMvc.perform(post("/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/users/{id} ───────────────────────────────────────────────────

    @Test
    @WithMockUser
    void getById_returns200_withUserResponse() throws Exception {
        User user = new User();
        UserResponse response = makeUserResponse(5L, "alice", 1L, "ADMIN");

        when(userService.getById(5L)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(response);

        mockMvc.perform(get("/api/users/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.roleCode").value("ADMIN"))
                .andExpect(jsonPath("$.groupIds").isArray())
                .andExpect(jsonPath("$.groupNames").isArray());
    }

    @Test
    @WithMockUser
    void getById_returns404_whenUserNotFound() throws Exception {
        when(userService.getById(999L)).thenThrow(new UserNotFoundException(999L));

        mockMvc.perform(get("/api/users/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    void getById_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/users ────────────────────────────────────────────────────────

    @Test
    @WithMockUser
    void listUsers_returns200_withSummaryList() throws Exception {
        User user = new User();
        UserSummaryResponse summary = new UserSummaryResponse(1L, "alice", "Alice Admin", 1L, "ADMIN", true);

        when(userService.findAll()).thenReturn(List.of(user));
        when(userMapper.toSummaryResponse(user)).thenReturn(summary);

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[0].roleCode").value("ADMIN"));
    }

    // ── PUT /api/users/{id} ───────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_USER_MANAGE")
    void updateUser_returns200_withUpdatedResponse() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest(
                "new@example.com", "New Name", 3L, true, List.of(3L), null
        );
        User user = new User();
        UserResponse response = makeUserResponse(7L, "bob", 3L, "VIEWER");

        when(userService.updateUser(eq(7L), any(UpdateUserRequest.class))).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(response);

        mockMvc.perform(put("/api/users/7")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleCode").value("VIEWER"));
    }

    @Test
    @WithMockUser(authorities = "PERM_USER_MANAGE")
    void updateUser_returns400_whenIsActiveIsNull() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest(
                "new@example.com", "New Name", 2L, null, null, null
        );

        mockMvc.perform(put("/api/users/7")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ── PATCH /api/users/{id}/activate & deactivate ───────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_USER_MANAGE")
    void activate_returns204() throws Exception {
        mockMvc.perform(patch("/api/users/3/activate").with(csrf()))
                .andExpect(status().isNoContent());
        verify(userService).activate(3L);
    }

    @Test
    @WithMockUser(authorities = "PERM_USER_MANAGE")
    void deactivate_returns204() throws Exception {
        mockMvc.perform(patch("/api/users/3/deactivate").with(csrf()))
                .andExpect(status().isNoContent());
        verify(userService).deactivate(3L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UserResponse makeUserResponse(Long id, String username, Long roleId, String roleCode) {
        return new UserResponse(id, username, username + "@example.com", "Full Name",
                roleId, roleCode, roleCode + " Role", true,
                List.of(1L), List.of("Support"), Instant.now(), null);
    }
}
