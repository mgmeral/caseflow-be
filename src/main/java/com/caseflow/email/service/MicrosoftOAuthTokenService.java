package com.caseflow.email.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches and caches Microsoft Entra ID (Azure AD) access tokens for IMAP XOAUTH2.
 *
 * <ul>
 *   <li>Uses the OAuth 2.0 client-credentials flow — app-only, no user interaction.</li>
 *   <li>Scope: {@code https://outlook.office365.com/.default}</li>
 *   <li>Tokens are cached per tenant+clientId key and proactively refreshed
 *       {@value #EXPIRY_BUFFER_SECONDS}s before expiry.</li>
 *   <li>Refresh-token and interactive flows are intentionally NOT used.</li>
 * </ul>
 */
@Service
public class MicrosoftOAuthTokenService {

    private static final Logger log = LoggerFactory.getLogger(MicrosoftOAuthTokenService.class);

    private static final String TOKEN_ENDPOINT_TEMPLATE =
            "https://login.microsoftonline.com/%s/oauth2/v2.0/token";
    private static final String SCOPE = "https://outlook.office365.com/.default";
    private static final int EXPIRY_BUFFER_SECONDS = 60;

    // Simple value object for cached tokens
    private record CachedToken(String accessToken, Instant expiresAt) {
        boolean isValid() {
            return Instant.now().plusSeconds(EXPIRY_BUFFER_SECONDS).isBefore(expiresAt);
        }
    }

    private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();
    private final HttpClient httpClient;

    // Matches "expires_in":3600 in the JSON response body
    private static final Pattern EXPIRES_IN_PATTERN = Pattern.compile("\"expires_in\"\\s*:\\s*(\\d+)");
    // Matches "access_token":"<value>" in the JSON response body
    private static final Pattern ACCESS_TOKEN_PATTERN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");

    public MicrosoftOAuthTokenService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Returns a valid access token for the given tenant and client credentials.
     * Fetches a new token from Azure AD when the cached one is absent or about to expire.
     *
     * @param tenantId     Azure AD tenant ID (GUID or domain)
     * @param clientId     Application (client) ID
     * @param clientSecret Client secret value
     * @return Bearer access token string
     * @throws IOException          on network failure
     * @throws InterruptedException on thread interruption
     */
    public String getAccessToken(String tenantId, String clientId, String clientSecret)
            throws IOException, InterruptedException {
        String cacheKey = tenantId + ":" + clientId;
        CachedToken cached = cache.get(cacheKey);
        if (cached != null && cached.isValid()) {
            log.debug("OAUTH2_TOKEN_CACHE_HIT tenantId={} clientId={}", tenantId, clientId);
            return cached.accessToken();
        }

        log.info("OAUTH2_TOKEN_FETCH tenantId={} clientId={}", tenantId, clientId);
        CachedToken fresh = fetchToken(tenantId, clientId, clientSecret);
        cache.put(cacheKey, fresh);
        return fresh.accessToken();
    }

    private CachedToken fetchToken(String tenantId, String clientId, String clientSecret)
            throws IOException, InterruptedException {
        String url = String.format(TOKEN_ENDPOINT_TEMPLATE, urlEncode(tenantId));
        String body = "grant_type=" + urlEncode("client_credentials")
                + "&client_id=" + urlEncode(clientId)
                + "&client_secret=" + urlEncode(clientSecret)
                + "&scope=" + urlEncode(SCOPE);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            // Avoid logging client_secret; log only status and a sanitized snippet
            String snippet = response.body().length() > 200
                    ? response.body().substring(0, 200) : response.body();
            throw new IOException("Microsoft OAuth2 token request failed — status: "
                    + response.statusCode() + ", body: " + snippet);
        }

        String responseBody = response.body();
        String accessToken = extractString(ACCESS_TOKEN_PATTERN, responseBody);
        if (accessToken == null) {
            throw new IOException("Microsoft OAuth2 response missing access_token");
        }
        long expiresIn = extractLong(EXPIRES_IN_PATTERN, responseBody, 3600L);
        Instant expiresAt = Instant.now().plusSeconds(expiresIn);

        log.info("OAUTH2_TOKEN_ACQUIRED tenantId={} clientId={} expiresIn={}s", tenantId, clientId, expiresIn);
        return new CachedToken(accessToken, expiresAt);
    }

    /** Evicts the cached token for the given tenant+clientId pair (e.g. after rotation). */
    public void evict(String tenantId, String clientId) {
        cache.remove(tenantId + ":" + clientId);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String extractString(Pattern pattern, String input) {
        Matcher m = pattern.matcher(input);
        return m.find() ? m.group(1) : null;
    }

    private static long extractLong(Pattern pattern, String input, long fallback) {
        Matcher m = pattern.matcher(input);
        if (m.find()) {
            try { return Long.parseLong(m.group(1)); } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }
}
