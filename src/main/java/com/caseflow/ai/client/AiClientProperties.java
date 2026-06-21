package com.caseflow.ai.client;

/**
 * Externalized configuration for the caseflow-ai-service HTTP client.
 *
 * <p>Bound and registered as a named bean ({@code aiClientProperties}) via
 * {@link com.caseflow.ai.config.AiClientConfig#aiClientProperties()}.
 * The explicit bean name is required so that {@code @Retryable} SpEL expressions
 * ({@code #{@aiClientProperties.retry.maxAttempts}}) can resolve it.
 */
public class AiClientProperties {

    private String baseUrl = "http://localhost:8081";
    private boolean enabled = true;
    private Timeout timeout = new Timeout();
    private Retry retry = new Retry();

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Timeout getTimeout() { return timeout; }
    public void setTimeout(Timeout timeout) { this.timeout = timeout; }

    public Retry getRetry() { return retry; }
    public void setRetry(Retry retry) { this.retry = retry; }

    public static class Timeout {
        private int connectMs = 3000;
        private int readMs = 15000;

        public int getConnectMs() { return connectMs; }
        public void setConnectMs(int connectMs) { this.connectMs = connectMs; }

        public int getReadMs() { return readMs; }
        public void setReadMs(int readMs) { this.readMs = readMs; }
    }

    public static class Retry {
        private int maxAttempts = 2;
        private long backoffMs = 500;

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

        public long getBackoffMs() { return backoffMs; }
        public void setBackoffMs(long backoffMs) { this.backoffMs = backoffMs; }
    }
}
