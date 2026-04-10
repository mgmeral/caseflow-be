package com.caseflow.integration.notification.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the event catalog (NotificationEventType.supportedValues()) is stable,
 * complete, and backward-compatible.
 */
class NotificationEventTypeTest {

    @Test
    void supportedValues_returnsAllEnumNames() {
        List<String> values = NotificationEventType.supportedValues();
        assertThat(values).hasSize(NotificationEventType.values().length);
    }

    @Test
    void supportedValues_includesCoreCatalogEvents() {
        List<String> values = NotificationEventType.supportedValues();

        // Ticket lifecycle
        assertThat(values).contains("TICKET_CREATED", "TICKET_REOPENED", "TICKET_RESOLVED", "TICKET_CLOSED");

        // Ownership / workflow
        assertThat(values).contains("TICKET_ASSIGNED", "TICKET_UNASSIGNED", "TICKET_TRANSFERRED",
                "ASSIGNEE_CHANGED", "GROUP_CHANGED");

        // Priority / SLA
        assertThat(values).contains("PRIORITY_CHANGED", "SLA_WARNING", "SLA_BREACHED", "SLA_RECOVERED");

        // Customer communication
        assertThat(values).contains("CUSTOMER_REPLIED", "OUTBOUND_EMAIL_FAILED",
                "SCHEDULED_EMAIL_SENT", "SCHEDULED_EMAIL_FAILED");

        // Collaboration / internal
        assertThat(values).contains("INTERNAL_NOTE_ADDED", "USER_MENTIONED_IN_NOTE");

        // Integration / delivery
        assertThat(values).contains("JIRA_ISSUE_CREATED", "JIRA_SYNC_FAILED", "WEBHOOK_DELIVERY_FAILED");
    }

    @Test
    void supportedValues_includesDeprecatedAlias_forBackwardCompatibility() {
        // OUTBOUND_REPLY_FAILED is @Deprecated but must remain in the catalog so that existing
        // channel configs that subscribed to the old event name continue to work.
        List<String> values = NotificationEventType.supportedValues();
        assertThat(values).contains("OUTBOUND_REPLY_FAILED");
    }

    @Test
    void supportedValues_returnsStrings_notEnumInstances() {
        List<String> values = NotificationEventType.supportedValues();
        assertThat(values).allSatisfy(v -> assertThat(v).isNotBlank());
        assertThat(values).allSatisfy(v -> assertThat(v).isUpperCase());
    }

    @Test
    void supportedValues_hasNoDuplicates() {
        List<String> values = NotificationEventType.supportedValues();
        assertThat(values).doesNotHaveDuplicates();
    }
}
