package com.caseflow.integration.jira.service;

import com.caseflow.integration.jira.domain.JiraConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class JiraApiClientDiagnoseTest {

    private static final String BASE = "https://acme.atlassian.net";

    private MockRestServiceServer server;
    private JiraApiClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new JiraApiClient(restTemplate);
    }

    private static JiraConfig config(String issueType) {
        JiraConfig c = new JiraConfig();
        c.setBaseUrl(BASE + "/");
        c.setAuthType("BASIC");
        c.setUsername("ops@acme.com");
        c.setApiToken("tok");
        c.setProjectKey("SUP");
        c.setIssueType(issueType);
        return c;
    }

    private void myselfOk() {
        server.expect(requestTo(BASE + "/rest/api/3/myself"))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Basic ")))
                .andRespond(withSuccess("{\"displayName\":\"Ops Bot\"}", MediaType.APPLICATION_JSON));
    }

    private void projectOk() {
        server.expect(requestTo(BASE + "/rest/api/3/project/SUP"))
                .andRespond(withSuccess("{\"key\":\"SUP\",\"name\":\"Support\"}", MediaType.APPLICATION_JSON));
    }

    private void issueTypes(String json) {
        server.expect(requestTo(BASE + "/rest/api/3/issue/createmeta/SUP/issuetypes"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    @Test
    void passesAllThreeChecks_andListsCreatableIssueTypes() {
        myselfOk();
        projectOk();
        issueTypes("{\"issueTypes\":[{\"name\":\"Task\"},{\"name\":\"Bug\"}]}");

        JiraApiClient.JiraDiagnostics d = client.diagnose(config("task"));

        assertThat(d.success()).isTrue();
        assertThat(d.checks()).extracting(JiraApiClient.JiraCheck::key).containsExactly("AUTH", "PROJECT", "ISSUE_TYPE");
        assertThat(d.checks().get(0).message()).contains("Ops Bot");
        assertThat(d.checks().get(1).message()).contains("Support");
        assertThat(d.issueTypes()).containsExactly("Task", "Bug");
        server.verify();
    }

    @Test
    void badCredentials_stopsAtAuth_andSkipsTheRest() {
        server.expect(requestTo(BASE + "/rest/api/3/myself")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        JiraApiClient.JiraDiagnostics d = client.diagnose(config("Task"));

        assertThat(d.success()).isFalse();
        assertThat(d.checks().get(0).ok()).isFalse();
        assertThat(d.checks().get(0).message()).contains("401").contains("API token");
        assertThat(d.checks().get(1).message()).startsWith("Skipped");
        assertThat(d.checks().get(2).message()).startsWith("Skipped");
        server.verify();
    }

    @Test
    void wrongSiteAddress_isExplained() {
        server.expect(requestTo(BASE + "/rest/api/3/myself")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.diagnose(config("Task")).checks().get(0).message()).contains("atlassian.net");
    }

    @Test
    void unknownProject_isReported() {
        myselfOk();
        server.expect(requestTo(BASE + "/rest/api/3/project/SUP")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        JiraApiClient.JiraDiagnostics d = client.diagnose(config("Task"));

        assertThat(d.checks().get(1).ok()).isFalse();
        assertThat(d.checks().get(1).message()).contains("SUP").contains("cannot see it");
        assertThat(d.checks().get(2).message()).startsWith("Skipped");
    }

    @Test
    void missingIssueType_listsWhatIsAvailable() {
        myselfOk();
        projectOk();
        issueTypes("{\"values\":[{\"name\":\"Görev\"},{\"name\":\"Hata\"}]}");

        JiraApiClient.JiraDiagnostics d = client.diagnose(config("Task"));

        assertThat(d.success()).isFalse();
        assertThat(d.checks().get(2).message()).contains("\"Task\"").contains("Görev, Hata");
        assertThat(d.issueTypes()).containsExactly("Görev", "Hata");
    }

    @Test
    void noCreatableIssueTypes_meansMissingCreatePermission() {
        myselfOk();
        projectOk();
        issueTypes("{\"issueTypes\":[]}");

        assertThat(client.diagnose(config("Task")).checks().get(2).message()).contains("Create issues");
    }
}
