package com.caseflow.integration.notification.repository;

import com.caseflow.integration.notification.domain.NotificationChannelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationChannelConfigRepository
        extends JpaRepository<NotificationChannelConfig, Long> {

    /**
     * Returns all enabled configs that may match the given event.
     *
     * <p>Filtering by subscribed event and scope happens in the service layer
     * since subscribed_events is a JSON column.
     */
    @Query("SELECT c FROM NotificationChannelConfig c WHERE c.isEnabled = true")
    List<NotificationChannelConfig> findAllEnabled();

    List<NotificationChannelConfig> findByIsEnabledTrue();
}
