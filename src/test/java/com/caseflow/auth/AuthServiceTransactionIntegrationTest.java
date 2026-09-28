package com.caseflow.auth;

import com.caseflow.identity.domain.Role;
import com.caseflow.identity.domain.TicketScope;
import com.caseflow.identity.domain.User;
import com.caseflow.identity.repository.RoleRepository;
import com.caseflow.identity.repository.UserRepository;
import com.caseflow.security.audit.SecurityAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks that the side effects of a rejected login or refresh are committed.
 * Both methods throw after writing (failed-attempt counter, lockout, revoking every
 * session on refresh-token reuse); a mocked-repository unit test cannot see a
 * rollback, so this runs AuthService's own transactions against real PostgreSQL.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("integration")
@Import({AuthService.class, JwtTokenService.class, JwtProperties.class, AuthServiceTransactionIntegrationTest.Config.class})
// No test-managed transaction: AuthService must commit or roll back on its own.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthServiceTransactionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("caseflow_test")
            .withUsername("caseflow")
            .withPassword("caseflow");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @TestConfiguration
    static class Config {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }
    }

    @MockBean private SecurityAuditService auditService;
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seed() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();

        Role role = new Role();
        role.setCode("AGENT");
        role.setName("Agent");
        role.setIsActive(true);
        role.setTicketScope(TicketScope.ALL);
        role = roleRepository.save(role);

        User user = new User();
        user.setUsername("dana");
        user.setEmail("dana@example.com");
        user.setFullName("Dana Agent");
        user.setIsActive(true);
        user.setRole(role);
        user.setPasswordHash(passwordEncoder.encode("correct-password"));
        userRepository.save(user);
    }

    @Test
    void failedLogins_areCounted_andLockTheAccount() {
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login("dana", "wrong"))
                    .isInstanceOf(BadCredentialsException.class);
        }

        User user = userRepository.findByUsername("dana").orElseThrow();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isNotNull();

        assertThatThrownBy(() -> authService.login("dana", "correct-password"))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void successfulLogin_resetsTheCounter() {
        assertThatThrownBy(() -> authService.login("dana", "wrong"))
                .isInstanceOf(BadCredentialsException.class);
        assertThat(userRepository.findByUsername("dana").orElseThrow().getFailedLoginAttempts()).isEqualTo(1);

        authService.login("dana", "correct-password");

        assertThat(userRepository.findByUsername("dana").orElseThrow().getFailedLoginAttempts()).isZero();
    }

    @Test
    void reusingARotatedRefreshToken_revokesEverySession() {
        AuthService.TokenPair first = authService.login("dana", "correct-password");
        AuthService.TokenPair rotated = authService.refresh(first.refreshToken());

        assertThatThrownBy(() -> authService.refresh(first.refreshToken()))
                .isInstanceOf(BadCredentialsException.class);

        // The legitimate, newer token must be dead too.
        assertThatThrownBy(() -> authService.refresh(rotated.refreshToken()))
                .isInstanceOf(BadCredentialsException.class);
    }
}
