package com.caseflow.identity.api;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.exception.UserProfileException;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.identity.api.dto.ChangePasswordRequest;
import com.caseflow.identity.api.dto.UpdateProfileRequest;
import com.caseflow.identity.api.dto.UserProfileResponse;
import com.caseflow.identity.api.mapper.UserMapper;
import com.caseflow.identity.domain.Role;
import com.caseflow.identity.domain.User;
import com.caseflow.identity.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserProfileControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private JwtTokenService jwtTokenService;
    @MockBean private CaseFlowUserDetailsService userDetailsService;
    @MockBean private UserService userService;
    @MockBean private UserMapper userMapper;

    private CaseFlowUserDetails principal;
    private UserProfileResponse profileResponse;

    @BeforeEach
    void setUp() {
        Role role = new Role();
        role.setCode("AGENT");
        role.setName("Agent");

        User user = new User();
        user.setUsername("jdoe");
        user.setEmail("j.doe@example.com");
        user.setFullName("John Doe");
        user.setIsActive(true);
        user.setRole(role);

        principal = new CaseFlowUserDetails(user);

        profileResponse = new UserProfileResponse(
                1L, "jdoe", "j.doe@example.com", "John Doe",
                "JD", "John", "Doe", "en-US", null,
                2L, "AGENT", "Agent",
                List.of(), List.of(),
                true, Instant.parse("2026-01-01T00:00:00Z"), null
        );
    }

    // ── GET /api/users/me ─────────────────────────────────────────────────────

    @Test
    void getMyProfile_returns200_withProfileFields() throws Exception {
        User domainUser = new User();
        when(userService.getMyProfile(any())).thenReturn(domainUser);
        when(userMapper.toProfileResponse(domainUser)).thenReturn(profileResponse);

        mockMvc.perform(get("/api/users/me").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("jdoe"))
                .andExpect(jsonPath("$.email").value("j.doe@example.com"))
                .andExpect(jsonPath("$.displayName").value("JD"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.locale").value("en-US"))
                .andExpect(jsonPath("$.avatarUrl").doesNotExist())
                .andExpect(jsonPath("$.roleCode").value("AGENT"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void getMyProfile_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /api/users/me ─────────────────────────────────────────────────────

    @Test
    void updateMyProfile_returns200_withUpdatedProfile() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("JD", "John", "Doe", "en-US");
        User domainUser = new User();
        when(userService.updateMyProfile(any(), any(UpdateProfileRequest.class))).thenReturn(domainUser);
        when(userMapper.toProfileResponse(domainUser)).thenReturn(profileResponse);

        mockMvc.perform(put("/api/users/me")
                        .with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("JD"))
                .andExpect(jsonPath("$.locale").value("en-US"));
    }

    @Test
    void updateMyProfile_returns400_whenDisplayNameTooLong() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest(
                "A".repeat(101), null, null, null);

        mockMvc.perform(put("/api/users/me")
                        .with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void updateMyProfile_returns401_whenUnauthenticated() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("JD", null, null, null);

        mockMvc.perform(put("/api/users/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/users/me/change-password ────────────────────────────────────

    @Test
    void changePassword_returns204_onSuccess() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("oldPass1", "newPass1");

        mockMvc.perform(post("/api/users/me/change-password")
                        .with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    @Test
    void changePassword_returns422_whenCurrentPasswordInvalid() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("wrongPass1", "newPass1");
        doThrow(new UserProfileException("CURRENT_PASSWORD_INVALID", "Current password is incorrect"))
                .when(userService).changePassword(any(), any());

        mockMvc.perform(post("/api/users/me/change-password")
                        .with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"));
    }

    @Test
    void changePassword_returns422_whenNewPasswordPolicyViolation() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("oldPass1", "onlyletters");
        doThrow(new UserProfileException("NEW_PASSWORD_POLICY_VIOLATION",
                "New password must be at least 8 characters and contain at least one letter and one digit"))
                .when(userService).changePassword(any(), any());

        mockMvc.perform(post("/api/users/me/change-password")
                        .with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NEW_PASSWORD_POLICY_VIOLATION"));
    }

    @Test
    void changePassword_returns400_whenNewPasswordTooShort() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("oldPass1", "short1");

        mockMvc.perform(post("/api/users/me/change-password")
                        .with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void changePassword_returns400_whenCurrentPasswordBlank() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("", "newPass1!");

        mockMvc.perform(post("/api/users/me/change-password")
                        .with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
