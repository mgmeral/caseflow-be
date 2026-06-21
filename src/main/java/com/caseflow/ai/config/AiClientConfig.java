package com.caseflow.ai.config;

import com.caseflow.ai.client.AiClientProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.RestClient;

/**
 * Configures the RestClient used by {@link com.caseflow.ai.client.CaseflowAiClient}.
 *
 * <p>A dedicated RestClient bean (qualifier {@code "aiRestClient"}) is created so
 * AI service timeouts are isolated from any other HTTP clients. {@link EnableRetry}
 * activates the {@code @Retryable} proxy on the AI client component.
 *
 * <p>The {@code aiClientProperties} bean is declared explicitly here so that
 * Spring Retry's SpEL expressions ({@code @aiClientProperties.retry.maxAttempts})
 * can resolve it by name — {@code @EnableConfigurationProperties} would register
 * the bean under the fully-qualified class name instead.
 */
@Configuration
@EnableRetry
public class AiClientConfig {

    @Bean("aiClientProperties")
    @ConfigurationProperties(prefix = "caseflow.ai.service")
    public AiClientProperties aiClientProperties() {
        return new AiClientProperties();
    }

    @Bean("aiRestClient")
    public RestClient aiRestClient(AiClientProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getTimeout().getConnectMs());
        factory.setReadTimeout(props.getTimeout().getReadMs());

        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(factory)
                .build();
    }
}
