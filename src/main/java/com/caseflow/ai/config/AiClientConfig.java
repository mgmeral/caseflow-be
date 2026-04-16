package com.caseflow.ai.config;

import com.caseflow.ai.client.AiClientProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
 */
@Configuration
@EnableRetry
@EnableConfigurationProperties(AiClientProperties.class)
public class AiClientConfig {

    @Bean("aiRestClient")
    public RestClient aiRestClient(AiClientProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getTimeout().getConnectMs());
        factory.setReadTimeout(props.getTimeout().getReadMs());

        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
