package com.caseflow.auth;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "caseflow.jwt")
public class JwtProperties {

    private static final String INSECURE_DEFAULT =
            "caseflow-default-secret-key-must-be-at-least-256-bits-long-for-hs256";
    private static final int MIN_SECRET_LENGTH = 32;

    private String secret;
    private long accessTokenExpirationMs = 3_600_000L;
    private long refreshTokenExpirationMs = 604_800_000L;

    @PostConstruct
    public void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "CASEFLOW_JWT_SECRET is not set. Set a strong random secret of at least 32 characters.");
        }
        if (secret.equals(INSECURE_DEFAULT)) {
            throw new IllegalStateException(
                    "CASEFLOW_JWT_SECRET is using the insecure hardcoded default. " +
                    "Set a unique random secret via the CASEFLOW_JWT_SECRET environment variable.");
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "CASEFLOW_JWT_SECRET must be at least " + MIN_SECRET_LENGTH +
                    " characters for HS256. Current length: " + secret.length());
        }
    }

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public long getAccessTokenExpirationMs() { return accessTokenExpirationMs; }
    public void setAccessTokenExpirationMs(long ms) { this.accessTokenExpirationMs = ms; }

    public long getRefreshTokenExpirationMs() { return refreshTokenExpirationMs; }
    public void setRefreshTokenExpirationMs(long ms) { this.refreshTokenExpirationMs = ms; }
}
