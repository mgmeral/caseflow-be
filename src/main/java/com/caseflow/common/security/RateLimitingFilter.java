package com.caseflow.common.security;

import com.caseflow.common.api.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP-based rate limiter for sensitive endpoints.
 *
 * <p>Auth endpoints (login/refresh/logout): 10 req / 60 s per IP — brute-force protection.
 * Contact by-email lookup: 20 req / 60 s per IP — PII enumeration protection.
 * AI endpoints (/api/ai/**): 20 req / 60 s per IP — LLM cost protection.
 * General API: 300 req / 60 s per IP — basic DoS protection.
 *
 * <p>State is in-process — sufficient for a single-node deployment.
 * For multi-node, replace the bucket store with Redis + Bucket4j's Redis backend.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final int AUTH_CAPACITY = 10;
    private static final int EMAIL_LOOKUP_CAPACITY = 20;
    private static final int AI_CAPACITY = 20;
    private static final int GENERAL_CAPACITY = 300;
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);

    // Separate bucket maps per rate-limit tier
    private final ConcurrentHashMap<String, Bucket> authBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> emailBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> aiBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> generalBuckets = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public RateLimitingFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Apply to all /api/** paths
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ip = resolveClientIp(request);
        String path = request.getRequestURI();
        Bucket bucket = resolveBucket(ip, path);

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(REFILL_PERIOD.toSeconds()));
            ErrorResponse error = ErrorResponse.of(
                    429, "Too Many Requests", "RATE_LIMIT_EXCEEDED",
                    "Too many requests — please wait before retrying",
                    path
            );
            objectMapper.writeValue(response.getWriter(), error);
        }
    }

    private Bucket resolveBucket(String ip, String path) {
        if (isAuthPath(path)) {
            return authBuckets.computeIfAbsent(ip, k -> newBucket(AUTH_CAPACITY));
        }
        if (path.equals("/api/contacts/by-email")) {
            return emailBuckets.computeIfAbsent(ip, k -> newBucket(EMAIL_LOOKUP_CAPACITY));
        }
        if (path.startsWith("/api/ai/")) {
            return aiBuckets.computeIfAbsent(ip, k -> newBucket(AI_CAPACITY));
        }
        return generalBuckets.computeIfAbsent(ip, k -> newBucket(GENERAL_CAPACITY));
    }

    private boolean isAuthPath(String path) {
        return path.equals("/api/auth/login")
                || path.equals("/api/auth/refresh")
                || path.equals("/api/auth/logout");
    }

    private Bucket newBucket(int capacity) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, REFILL_PERIOD)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
