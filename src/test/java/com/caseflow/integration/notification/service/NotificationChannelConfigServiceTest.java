package com.caseflow.integration.notification.service;

import com.caseflow.integration.notification.domain.ChannelType;
import com.caseflow.integration.notification.domain.NotificationChannelConfig;
import com.caseflow.integration.notification.domain.NotificationEventType;
import com.caseflow.integration.notification.repository.NotificationChannelConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationChannelConfigServiceTest {

    @Mock
    private NotificationChannelConfigRepository repository;

    @InjectMocks
    private NotificationChannelConfigService service;

    @Test
    void create_savesConfigWithSerializedEvents() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationChannelConfig result = service.create(
                "Ops Slack", ChannelType.SLACK, "https://hooks.slack.com/test",
                List.of(NotificationEventType.TICKET_CREATED, NotificationEventType.TICKET_RESOLVED),
                "GLOBAL", null, true, 1L
        );

        assertThat(result.getName()).isEqualTo("Ops Slack");
        assertThat(result.getChannelType()).isEqualTo(ChannelType.SLACK);
        assertThat(result.getSubscribedEvents()).contains("TICKET_CREATED");
        assertThat(result.getSubscribedEvents()).contains("TICKET_RESOLVED");
        assertThat(result.getScopeType()).isEqualTo("GLOBAL");
    }

    @Test
    void findById_throwsWhenNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void delete_removesConfig() {
        when(repository.existsById(1L)).thenReturn(true);

        service.delete(1L);

        verify(repository).deleteById(1L);
    }

    @Test
    void delete_throwsWhenNotFound() {
        when(repository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void update_preservesWebhookUrl_whenBlankProvided() {
        NotificationChannelConfig existing = new NotificationChannelConfig();
        existing.setWebhookUrl("https://original-webhook.com");
        existing.setChannelType(ChannelType.SLACK);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationChannelConfig updated = service.update(
                1L, "New Name", ChannelType.SLACK, "", // blank webhookUrl
                List.of(NotificationEventType.TICKET_CLOSED),
                "GLOBAL", null, true
        );

        assertThat(updated.getWebhookUrl()).isEqualTo("https://original-webhook.com");
    }
}
