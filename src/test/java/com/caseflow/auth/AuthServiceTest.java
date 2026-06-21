package com.caseflow.auth;

import com.caseflow.identity.domain.User;
import com.caseflow.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtTokenService jwtTokenService;
    @Mock private JwtProperties jwtProperties;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUsername("alice");
        user.setPasswordHash("$hashed$");
        user.setIsActive(true);
        user.setFailedLoginAttempts(0);
    }

    @Test
    void login_succeeds_andResetsFailedAttempts() {
        user.setFailedLoginAttempts(2);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass", "$hashed$")).thenReturn(true);
        when(jwtTokenService.generateAccessToken(any(), any())).thenReturn("tok");
        when(jwtProperties.getRefreshTokenExpirationMs()).thenReturn(86400000L);
        when(jwtProperties.getAccessTokenExpirationMs()).thenReturn(3600000L);
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        authService.login("alice", "pass");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginAttempts()).isZero();
        assertThat(captor.getValue().getLockedUntil()).isNull();
    }

    @Test
    void login_incrementsFailedAttempts_onBadPassword() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$hashed$")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("alice", "wrong"))
                .isInstanceOf(BadCredentialsException.class);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void login_locksAccount_afterFiveFailures() {
        user.setFailedLoginAttempts(4);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$hashed$")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("alice", "wrong"))
                .isInstanceOf(BadCredentialsException.class);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginAttempts()).isEqualTo(5);
        assertThat(captor.getValue().getLockedUntil()).isNotNull()
                .isAfter(Instant.now());
    }

    @Test
    void login_rejects_whenAccountLocked() {
        user.setLockedUntil(Instant.now().plusSeconds(300));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login("alice", "pass"))
                .isInstanceOf(AccountLockedException.class);

        verify(userRepository, times(0)).save(any());
    }

    @Test
    void login_allows_whenLockExpired() {
        user.setLockedUntil(Instant.now().minusSeconds(1));
        user.setFailedLoginAttempts(5);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass", "$hashed$")).thenReturn(true);
        when(jwtTokenService.generateAccessToken(any(), any())).thenReturn("tok");
        when(jwtProperties.getRefreshTokenExpirationMs()).thenReturn(86400000L);
        when(jwtProperties.getAccessTokenExpirationMs()).thenReturn(3600000L);
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        authService.login("alice", "pass");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedLoginAttempts()).isZero();
        assertThat(captor.getValue().getLockedUntil()).isNull();
    }
}
