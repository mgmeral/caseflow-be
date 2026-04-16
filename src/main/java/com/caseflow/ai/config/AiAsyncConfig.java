package com.caseflow.ai.config;

import com.caseflow.ai.event.dto.AiSyncEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer configuration for AI event publishing.
 *
 * <p>All beans are conditional on {@code caseflow.ai.async.enabled=true}.
 * When async is disabled (default), no Kafka beans are created and the application
 * starts safely without any Kafka broker available.
 *
 * <p>If Kafka is enabled but the broker is unreachable, publishing failures are
 * logged at ERROR level but never propagate to request flows.
 */
@Configuration
@EnableConfigurationProperties(AiAsyncProperties.class)
public class AiAsyncConfig {

    /**
     * Kafka producer factory — only created when async is enabled.
     * Uses JSON serialization for event payloads.
     */
    @Bean
    @ConditionalOnProperty(name = "caseflow.ai.async.enabled", havingValue = "true")
    public ProducerFactory<String, AiSyncEvent> aiKafkaProducerFactory(AiAsyncProperties props) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, props.getKafka().getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Idempotent producer for exactly-once delivery semantics
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        return new DefaultKafkaProducerFactory<>(config);
    }

    /**
     * KafkaTemplate for AiSyncEvent — only created when async is enabled.
     */
    @Bean
    @ConditionalOnProperty(name = "caseflow.ai.async.enabled", havingValue = "true")
    public KafkaTemplate<String, AiSyncEvent> aiKafkaTemplate(
            ProducerFactory<String, AiSyncEvent> aiKafkaProducerFactory) {
        return new KafkaTemplate<>(aiKafkaProducerFactory);
    }
}
