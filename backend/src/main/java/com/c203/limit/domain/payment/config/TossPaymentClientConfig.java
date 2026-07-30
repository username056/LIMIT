package com.c203.limit.domain.payment.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class TossPaymentClientConfig {
    @Bean
    @Qualifier("tossPaymentRestClientBuilder")
    RestClient.Builder tossPaymentRestClientBuilder() {
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder().requestFactory(requestFactory);
    }
}
