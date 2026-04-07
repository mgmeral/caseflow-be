package com.caseflow.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for Phase 2 integration infrastructure.
 */
@Configuration
public class IntegrationConfig {

    /**
     * RestTemplate used by Jira and webhook notification clients.
     *
     * <p>Uses default timeouts. In production, consider wrapping with
     * a timeout-aware RequestFactory (e.g. HttpComponentsClientHttpRequestFactory).
     */
    @Bean
    public RestTemplate integrationRestTemplate() {
        return new RestTemplate();
    }
}
