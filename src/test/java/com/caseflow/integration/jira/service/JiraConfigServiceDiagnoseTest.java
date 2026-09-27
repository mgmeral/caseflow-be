package com.caseflow.integration.jira.service;

import com.caseflow.integration.jira.domain.JiraConfig;
import com.caseflow.integration.jira.repository.JiraConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JiraConfigServiceDiagnoseTest {

    @Mock private JiraConfigRepository repository;
    @Mock private JiraApiClient apiClient;

    private JiraConfigService service;

    @BeforeEach
    void setUp() {
        service = new JiraConfigService(repository, apiClient);
        JiraConfig saved = new JiraConfig();
        saved.setBaseUrl("https://acme.atlassian.net/");
        saved.setApiToken("saved-token");
        lenient().when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(saved));
        lenient().when(apiClient.diagnose(any())).thenReturn(new JiraApiClient.JiraDiagnostics(true, List.of(), List.of()));
    }

    private JiraConfig diagnosed() {
        ArgumentCaptor<JiraConfig> captor = ArgumentCaptor.forClass(JiraConfig.class);
        verify(apiClient).diagnose(captor.capture());
        return captor.getValue();
    }

    @Test
    void testsTheUnsavedFormValues_withoutSavingThem() {
        service.diagnose(" https://acme.atlassian.net ", " ops@acme.com ", "new-token", " SUP ", "Bug");

        JiraConfig draft = diagnosed();
        assertThat(draft.getBaseUrl()).isEqualTo("https://acme.atlassian.net");
        assertThat(draft.getUsername()).isEqualTo("ops@acme.com");
        assertThat(draft.getApiToken()).isEqualTo("new-token");
        assertThat(draft.getProjectKey()).isEqualTo("SUP");
        assertThat(draft.getIssueType()).isEqualTo("Bug");
        verify(repository, never()).save(any());
    }

    @Test
    void blankToken_reusesTheSavedOne_forTheSameAddress() {
        service.diagnose("https://ACME.atlassian.net", "ops@acme.com", "  ", "SUP", null);

        assertThat(diagnosed().getApiToken()).isEqualTo("saved-token");
    }

    @Test
    void blankToken_isRefused_forADifferentAddress() {
        assertThatThrownBy(() -> service.diagnose("https://evil.example.com", "ops@acme.com", null, "SUP", "Task"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API token");
        verify(apiClient, never()).diagnose(any());
    }

    @Test
    void blankToken_isRefused_whenNothingIsSaved() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.diagnose("https://acme.atlassian.net", "ops@acme.com", "", "SUP", "Task"))
                .isInstanceOf(IllegalStateException.class);
    }
}
