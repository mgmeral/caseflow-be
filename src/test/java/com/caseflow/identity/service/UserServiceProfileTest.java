package com.caseflow.identity.service;

import com.caseflow.common.exception.UserNotFoundException;
import com.caseflow.common.exception.UserProfileException;
import com.caseflow.identity.api.dto.ChangePasswordRequest;
import com.caseflow.identity.api.dto.UpdateProfileRequest;
import com.caseflow.identity.domain.Role;
import com.caseflow.identity.domain.User;
import com.caseflow.identity.repository.GroupRepository;
import com.caseflow.identity.repository.RoleRepository;
import com.caseflow.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceProfileTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService service;

    private User user;

    @BeforeEach
    void setUp() {
        Role role = new Role();
        role.setCode("AGENT");
        role.setName("Agent");

        user = new User();
        user.setUsername("jdoe");
        user.setEmail("j.doe@example.com");
        user.setFullName("John Doe");
        user.setPasswordHash("$2a$10$hashed");
        user.setIsActive(true);
        user.setRole(role);
    }

    // ── getMyProfile ──────────────────────────────────────────────────────────

    @Test
    void getMyProfile_returnsUser_whenFound() {
        when(userRepository.findByIdWithRoleAndGroups(1L)).thenReturn(Optional.of(user));

        User result = service.getMyProfile(1L);

        assertThat(result.getUsername()).isEqualTo("jdoe");
    }

    @Test
    void getMyProfile_throwsUserNotFound_whenMissing() {
        when(userRepository.findByIdWithRoleAndGroups(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyProfile(99L))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ── updateMyProfile ───────────────────────────────────────────────────────

    @Test
    void updateMyProfile_updatesAllEditableFields() {
        when(userRepository.findByIdWithRoleAndGroups(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest("JD", "John", "Doe", "en-US");
        User result = service.updateMyProfile(1L, request);

        assertThat(result.getDisplayName()).isEqualTo("JD");
        assertThat(result.getFirstName()).isEqualTo("John");
        assertThat(result.getLastName()).isEqualTo("Doe");
        assertThat(result.getLocale()).isEqualTo("en-US");
    }

    @Test
    void updateMyProfile_doesNotChangeRoleOrPassword() {
        when(userRepository.findByIdWithRoleAndGroups(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest(null, null, null, null);
        User result = service.updateMyProfile(1L, request);

        assertThat(result.getRole().getCode()).isEqualTo("AGENT");
        assertThat(result.getPasswordHash()).isEqualTo("$2a$10$hashed");
    }

    // ── changePassword ────────────────────────────────────────────────────────

    @Test
    void changePassword_succeeds_whenCurrentPasswordMatches() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldPass1", "$2a$10$hashed")).thenReturn(true);
        when(passwordEncoder.encode("newPass1")).thenReturn("$2a$10$newHashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.changePassword(1L, new ChangePasswordRequest("oldPass1", "newPass1"));

        verify(userRepository).save(user);
        assertThat(user.getPasswordHash()).isEqualTo("$2a$10$newHashed");
    }

    @Test
    void changePassword_throwsCurrentPasswordInvalid_whenMismatch() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$2a$10$hashed")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(1L,
                new ChangePasswordRequest("wrong", "newPass1")))
                .isInstanceOf(UserProfileException.class)
                .extracting(e -> ((UserProfileException) e).getCode())
                .isEqualTo("CURRENT_PASSWORD_INVALID");
    }

    @Test
    void changePassword_throwsNewPasswordPolicyViolation_whenNoDigit() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldPass1", "$2a$10$hashed")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(1L,
                new ChangePasswordRequest("oldPass1", "onlyletters")))
                .isInstanceOf(UserProfileException.class)
                .extracting(e -> ((UserProfileException) e).getCode())
                .isEqualTo("NEW_PASSWORD_POLICY_VIOLATION");
    }

    @Test
    void changePassword_throwsNewPasswordPolicyViolation_whenNoLetter() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldPass1", "$2a$10$hashed")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(1L,
                new ChangePasswordRequest("oldPass1", "12345678")))
                .isInstanceOf(UserProfileException.class)
                .extracting(e -> ((UserProfileException) e).getCode())
                .isEqualTo("NEW_PASSWORD_POLICY_VIOLATION");
    }
}
