package com.caseflow.integration.notification.service;

import com.caseflow.integration.notification.domain.ChannelType;
import com.caseflow.integration.notification.domain.NotificationChannelConfig;
import com.caseflow.integration.notification.domain.NotificationEventType;
import com.caseflow.integration.notification.repository.NotificationChannelConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages notification channel configurations.
 */
@Service
public class NotificationChannelConfigService {

    private final NotificationChannelConfigRepository repository;

    public NotificationChannelConfigService(NotificationChannelConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<NotificationChannelConfig> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public NotificationChannelConfig findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Notification channel config not found: " + id));
    }

    @Transactional
    public NotificationChannelConfig create(String name, ChannelType channelType,
                                             String webhookUrl,
                                             List<NotificationEventType> subscribedEvents,
                                             String scopeType, Long scopeId,
                                             boolean enabled, Long createdBy) {
        NotificationChannelConfig config = new NotificationChannelConfig();
        config.setName(name);
        config.setChannelType(channelType);
        config.setWebhookUrl(webhookUrl);
        config.setSubscribedEvents(eventsToJson(subscribedEvents));
        config.setScopeType(scopeType != null ? scopeType : "GLOBAL");
        config.setScopeId(scopeId);
        config.setIsEnabled(enabled);
        config.setCreatedBy(createdBy);
        return repository.save(config);
    }

    @Transactional
    public NotificationChannelConfig update(Long id, String name, ChannelType channelType,
                                             String webhookUrl,
                                             List<NotificationEventType> subscribedEvents,
                                             String scopeType, Long scopeId,
                                             boolean enabled) {
        NotificationChannelConfig config = findById(id);
        config.setName(name);
        config.setChannelType(channelType);
        if (webhookUrl != null && !webhookUrl.isBlank()) {
            config.setWebhookUrl(webhookUrl);
        }
        config.setSubscribedEvents(eventsToJson(subscribedEvents));
        config.setScopeType(scopeType != null ? scopeType : "GLOBAL");
        config.setScopeId(scopeId);
        config.setIsEnabled(enabled);
        return repository.save(config);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Notification channel config not found: " + id);
        }
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<NotificationChannelConfig> findEnabled() {
        return repository.findAllEnabled();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String eventsToJson(List<NotificationEventType> events) {
        if (events == null || events.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < events.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(events.get(i).name()).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }
}
