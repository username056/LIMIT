package com.c203.limit.domain.inspection.checklist;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(
        prefix = "limit.ai.checklist",
        name = "enabled",
        havingValue = "true")
public class ChecklistAiClientConfig {

    @Bean
    @Qualifier("checklistAiRestClientBuilder")
    RestClient.Builder checklistAiRestClientBuilder(
            @Value("${limit.ai.checklist.connect-timeout:5s}") Duration connectTimeout,
            @Value("${limit.ai.checklist.read-timeout:30s}") Duration readTimeout) {
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(requestFactory);
    }
}
