package com.caseflow.ai.service;

import com.caseflow.ai.client.AiClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Simple availability guard that checks the {@code caseflow.ai.service.enabled} flag.
 *
 * <p>A circuit-breaker or health-check probe can be layered here in the future.
 * For now, disabled mode is controlled by configuration, not runtime health state.
 */
@Service
public class AiAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(AiAvailabilityService.class);

    private final AiClientProperties properties;

    public AiAvailabilityService(AiClientProperties properties) {
        this.properties = properties;
    }

    /**
     * Returns true when the AI service integration is active.
     * Returns false when {@code caseflow.ai.service.enabled=false}.
     */
    public boolean isAvailable() {
        if (!properties.isEnabled()) {
            log.debug("AI service is disabled via caseflow.ai.service.enabled=false");
            return false;
        }
        return true;
    }
}
