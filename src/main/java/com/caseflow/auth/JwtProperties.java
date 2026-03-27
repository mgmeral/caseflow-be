package com.caseflow.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "caseflow.jwt")
public class JwtProperties {

    /** Must be at least 256 bits (32 chars) for HS256. Override via CASEFLOW_JWT_SECRET env var. */
    private String secret = "caseflow-default-secret-key-must-be-at-least-256-bits-long-for-hs256";
    private long accessTokenExpirationMs = 3_600_000L;      // 1 hour
    private long refreshTokenExpirationMs = 604_800_000L;   // 7 days

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public long getAccessTokenExpirationMs() { return accessTokenExpirationMs; }
    public void setAccessTokenExpirationMs(long ms) { this.accessTokenExpirationMs = ms; }

    public long getRefreshTokenExpirationMs() { return refreshTokenExpirationMs; }
    public void setRefreshTokenExpirationMs(long ms) { this.refreshTokenExpirationMs = ms; }
}
