package com.caseflow.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitingFilterTest {

    private RateLimitingFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitingFilter(new ObjectMapper().registerModule(new JavaTimeModule()));
        chain = mock(FilterChain.class);
    }

    // ── shouldNotFilter ────────────────────────────────────────────────────────

    @Test
    void shouldNotFilter_trueForNonApiPaths() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilter_falseForApiPaths() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/tickets");
        assertThat(filter.shouldNotFilter(req)).isFalse();
    }

    // ── Normal requests pass through ──────────────────────────────────────────

    @Test
    void generalRequest_passesThrough() throws Exception {
        MockHttpServletRequest req = request("GET", "/api/tickets", "1.2.3.4");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
        verify(chain, times(1)).doFilter(req, resp);
    }

    @Test
    void authRequest_passesThrough_withinLimit() throws Exception {
        MockHttpServletRequest req = request("POST", "/api/auth/login", "10.0.0.1");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
        verify(chain).doFilter(req, resp);
    }

    // ── Rate limit exhaustion → 429 ───────────────────────────────────────────

    @Test
    void authEndpoint_returns429_afterExceedingLimit() throws Exception {
        String ip = "5.5.5.5";
        // Auth bucket capacity = 10; exhaust it
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = request("POST", "/api/auth/login", ip);
            filter.doFilter(req, new MockHttpServletResponse(), chain);
        }

        MockHttpServletRequest req = request("POST", "/api/auth/login", ip);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(429);
        assertThat(resp.getHeader("Retry-After")).isEqualTo("60");
        assertThat(resp.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
    }

    @Test
    void aiEndpoint_returns429_afterExceedingLimit() throws Exception {
        String ip = "6.6.6.6";
        // AI bucket capacity = 20
        for (int i = 0; i < 20; i++) {
            MockHttpServletRequest req = request("GET", "/api/ai/tickets/1/summary", ip);
            filter.doFilter(req, new MockHttpServletResponse(), chain);
        }

        MockHttpServletRequest req = request("GET", "/api/ai/tickets/1/summary", ip);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(429);
    }

    @Test
    void ticketAiEndpoint_usesAiBucket_returns429AfterLimit() throws Exception {
        String ip = "11.11.11.11";
        // AI bucket capacity = 20; exhaust with ticket AI path
        for (int i = 0; i < 20; i++) {
            filter.doFilter(request("GET", "/api/tickets/1/ai-summary", ip),
                    new MockHttpServletResponse(), chain);
        }

        MockHttpServletRequest req = request("GET", "/api/tickets/1/ai-summary", ip);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(429);
    }

    // ── Different IPs have independent buckets ────────────────────────────────

    @Test
    void differentIps_haveIndependentBuckets() throws Exception {
        // Exhaust bucket for IP A
        for (int i = 0; i < 10; i++) {
            filter.doFilter(request("POST", "/api/auth/login", "7.7.7.7"),
                    new MockHttpServletResponse(), chain);
        }

        // IP B should still pass
        MockHttpServletRequest req = request("POST", "/api/auth/login", "8.8.8.8");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
    }

    // ── X-Forwarded-For header ────────────────────────────────────────────────

    @Test
    void xForwardedFor_usedAsClientIp() throws Exception {
        // Exhaust the auth bucket for the proxied IP
        String proxiedIp = "9.9.9.9";
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = request("POST", "/api/auth/login", "proxy-ip");
            req.addHeader("X-Forwarded-For", proxiedIp + ", 10.0.0.1");
            filter.doFilter(req, new MockHttpServletResponse(), chain);
        }

        MockHttpServletRequest req = request("POST", "/api/auth/login", "proxy-ip");
        req.addHeader("X-Forwarded-For", proxiedIp + ", 10.0.0.1");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(429);
    }

    @Test
    void xForwardedFor_takesFirstIpFromList() throws Exception {
        // First IP in list should be the bucket key; "real-client" not exhausted for "other-ip"
        MockHttpServletRequest req = request("GET", "/api/tickets", "proxy");
        req.addHeader("X-Forwarded-For", "real-client, 192.168.1.1");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(200);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MockHttpServletRequest request(String method, String path, String remoteAddr) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        req.setRemoteAddr(remoteAddr);
        return req;
    }
}
