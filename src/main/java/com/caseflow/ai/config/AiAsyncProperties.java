package com.caseflow.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the AI async (Kafka) publishing lane.
 * Registered via {@link AiAsyncConfig}.
 */
@ConfigurationProperties(prefix = "caseflow.ai.async")
public class AiAsyncProperties {

    /** Master switch — when false, {@link com.caseflow.ai.event.NoOpAiEventPublisher} is used. */
    private boolean enabled = false;

    private Kafka kafka = new Kafka();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Kafka getKafka() { return kafka; }
    public void setKafka(Kafka kafka) { this.kafka = kafka; }

    public static class Kafka {
        private String bootstrapServers = "localhost:9092";
        private Topic topic = new Topic();

        public String getBootstrapServers() { return bootstrapServers; }
        public void setBootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
        }

        public Topic getTopic() { return topic; }
        public void setTopic(Topic topic) { this.topic = topic; }
    }

    public static class Topic {
        private String ticketSync = "ticket-ai-sync-requested";
        private String policyIngest = "policy-ai-ingest-requested";
        private String templateIngest = "template-ai-ingest-requested";

        public String getTicketSync() { return ticketSync; }
        public void setTicketSync(String ticketSync) { this.ticketSync = ticketSync; }

        public String getPolicyIngest() { return policyIngest; }
        public void setPolicyIngest(String policyIngest) { this.policyIngest = policyIngest; }

        public String getTemplateIngest() { return templateIngest; }
        public void setTemplateIngest(String templateIngest) { this.templateIngest = templateIngest; }
    }
}
