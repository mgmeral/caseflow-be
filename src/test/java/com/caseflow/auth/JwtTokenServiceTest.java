package com.caseflow.auth;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtTokenServiceTest {

    private static final String VALID_SECRET =
            "test-secret-key-that-is-at-least-thirty-two-chars-long";

    private JwtProperties props;
    private JwtTokenService service;

    @BeforeEach
    void setUp() {
        props = mock(JwtProperties.class);
        when(props.getSecret()).thenReturn(VALID_SECRET);
        when(props.getAccessTokenExpirationMs()).thenReturn(3_600_000L);
        service = new JwtTokenService(props);
    }

    @Test
    void generateAndValidate_roundTrip() {
        String token = service.generateAccessToken(42L, "alice");

        var claims = service.validateAndParseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("username", String.class)).isEqualTo("alice");
    }

    @Test
    void extractUserId_returnsCorrectId() {
        String token = service.generateAccessToken(99L, "bob");

        assertThat(service.extractUserId(token)).isEqualTo(99L);
    }

    @Test
    void validateAndParseClaims_throwsOnExpiredToken() {
        when(props.getAccessTokenExpirationMs()).thenReturn(-1L);
        JwtTokenService expiredService = new JwtTokenService(props);
        String expiredToken = expiredService.generateAccessToken(1L, "user");

        assertThatThrownBy(() -> service.validateAndParseClaims(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void validateAndParseClaims_throwsOnMalformedToken() {
        assertThatThrownBy(() -> service.validateAndParseClaims("not.a.jwt"))
                .isInstanceOf(MalformedJwtException.class);
    }

    @Test
    void validateAndParseClaims_throwsOnEmptyString() {
        assertThatThrownBy(() -> service.validateAndParseClaims(""))
                .isInstanceOf(Exception.class);
    }

    @Test
    void validateAndParseClaims_throwsOnWrongSignature() {
        JwtProperties otherProps = mock(JwtProperties.class);
        when(otherProps.getSecret()).thenReturn("different-secret-key-that-is-long-enough-for-hs256");
        when(otherProps.getAccessTokenExpirationMs()).thenReturn(3_600_000L);
        JwtTokenService other = new JwtTokenService(otherProps);

        String tokenFromOtherKey = other.generateAccessToken(1L, "user");

        assertThatThrownBy(() -> service.validateAndParseClaims(tokenFromOtherKey))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void generateAccessToken_doesNotEmbedSensitiveData() {
        String token = service.generateAccessToken(1L, "alice");

        // Decode payload (middle part) without verification to check claims
        String payload = new String(java.util.Base64.getUrlDecoder()
                .decode(token.split("\\.")[1]));
        assertThat(payload).doesNotContain("password")
                           .doesNotContain("hash")
                           .doesNotContain("role");
    }
}
