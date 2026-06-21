package com.caseflow.auth;

import com.caseflow.identity.domain.User;
import com.caseflow.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final java.time.Duration LOCKOUT_DURATION = java.time.Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtTokenService jwtTokenService,
                       JwtProperties jwtProperties,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenService = jwtTokenService;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public TokenPair login(String username, String password) {
        log.info("Login attempt — username: {}", username);
        User user = userRepository.findByUsername(username)
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .orElseGet(() -> {
                    log.warn("Login failed — user not found or inactive: {}", username);
                    throw new BadCredentialsException("Invalid credentials");
                });

        if (user.isLocked()) {
            log.warn("Login blocked — account locked until: {}, username: {}", user.getLockedUntil(), username);
            throw new AccountLockedException(user.getLockedUntil());
        }

        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(Instant.now().plus(LOCKOUT_DURATION));
                log.warn("Account locked — too many failed attempts, username: {}, until: {}", username, user.getLockedUntil());
            }
            userRepository.save(user);
            log.warn("Login failed — invalid password for username: {} (attempt {})", username, attempts);
            throw new BadCredentialsException("Invalid credentials");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        log.info("Login successful — username: {}, userId: {}", username, user.getId());
        return generateTokenPair(user);
    }

    @Transactional
    public TokenPair refresh(String rawRefreshToken) {
        String hash = hashToken(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseGet(() -> {
                    log.warn("Token refresh failed — refresh token not found");
                    throw new BadCredentialsException("Invalid refresh token");
                });

        if (stored.isRevoked()) {
            // Token theft detected — revoke all sessions for this user
            log.warn("Refresh token reuse detected (possible theft) — revoking all sessions for userId: {}", stored.getUserId());
            refreshTokenRepository.revokeAllForUser(stored.getUserId());
            throw new BadCredentialsException("Refresh token already used — all sessions revoked");
        }

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            log.warn("Token refresh failed — token expired for userId: {}", stored.getUserId());
            throw new BadCredentialsException("Refresh token expired");
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        log.info("Token refresh successful — userId: {}", user.getId());
        return generateTokenPair(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
            log.info("Logout — refresh token revoked for userId: {}", rt.getUserId());
        });
    }

    @Transactional(readOnly = true)
    public User getAuthenticatedUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));
    }

    private TokenPair generateTokenPair(User user) {
        String accessToken = jwtTokenService.generateAccessToken(user.getId(), user.getUsername());

        enforceSessionLimit(user.getId());

        String rawRefreshToken = UUID.randomUUID().toString();
        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setTokenHash(hashToken(rawRefreshToken));
        rt.setExpiresAt(Instant.now().plusMillis(jwtProperties.getRefreshTokenExpirationMs()));
        refreshTokenRepository.save(rt);

        return new TokenPair(accessToken, rawRefreshToken, jwtProperties.getAccessTokenExpirationMs() / 1000);
    }

    private void enforceSessionLimit(Long userId) {
        int max = jwtProperties.getMaxConcurrentSessions();
        long active = refreshTokenRepository.countByUserIdAndRevokedFalseAndExpiresAtAfter(userId, Instant.now());
        if (active >= max) {
            int toRevoke = (int) (active - max + 1);
            List<RefreshToken> oldest = refreshTokenRepository
                    .findActiveByUserIdOrderByCreatedAsc(userId, Instant.now(), PageRequest.of(0, toRevoke));
            oldest.forEach(t -> t.setRevoked(true));
            refreshTokenRepository.saveAll(oldest);
            log.info("Session limit ({}) reached for userId: {} — revoked {} oldest session(s)", max, userId, oldest.size());
        }
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public record TokenPair(String accessToken, String refreshToken, long expiresIn) {}
}
