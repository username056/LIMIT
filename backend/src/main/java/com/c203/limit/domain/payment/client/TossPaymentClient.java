package com.c203.limit.domain.payment.client;

import com.c203.limit.domain.payment.config.TossPaymentProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/** Toss Payments 결제 승인·조회 API 클라이언트. */
@Component
public class TossPaymentClient {
    private static final String BASE_URL = "https://api.tosspayments.com";
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String API_VERSION_HEADER = "TossPayments-api-version";

    private final RestClient restClient;
    private final TossPaymentProperties properties;
    private final ObjectMapper objectMapper;

    public TossPaymentClient(
            @Qualifier("tossPaymentRestClientBuilder") RestClient.Builder restClientBuilder,
            TossPaymentProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public TossPaymentResponse confirm(String paymentKey, String orderId, long amount, String idempotencyKey) {
        try {
            return restClient
                    .post()
                    .uri("/v1/payments/confirm")
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeader())
                    .header(IDEMPOTENCY_HEADER, idempotencyKey)
                    .headers(this::applyApiVersion)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TossConfirmRequest(paymentKey, orderId, amount))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::handleErrorResponse)
                    .body(TossPaymentResponse.class);
        } catch (ResourceAccessException exception) {
            throw TossPaymentClientException.networkFailure(exception);
        }
    }

    public TossPaymentResponse findByOrderId(String orderId) {
        try {
            return restClient
                    .get()
                    .uri("/v1/payments/orders/{orderId}", orderId)
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeader())
                    .headers(this::applyApiVersion)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::handleErrorResponse)
                    .body(TossPaymentResponse.class);
        } catch (ResourceAccessException exception) {
            throw TossPaymentClientException.networkFailure(exception);
        }
    }

    private void handleErrorResponse(HttpRequest request, ClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        try {
            TossErrorResponse errorBody = objectMapper.readValue(response.getBody(), TossErrorResponse.class);
            throw TossPaymentClientException.of(status, errorBody.code(), errorBody.message());
        } catch (IOException parseFailure) {
            throw TossPaymentClientException.of(status, "UNKNOWN", "Toss 응답을 해석할 수 없습니다.");
        }
    }

    private void applyApiVersion(HttpHeaders headers) {
        String apiVersion = properties.getApiVersion();
        if (apiVersion != null && !apiVersion.isBlank()) {
            headers.add(API_VERSION_HEADER, apiVersion);
        }
    }

    private String basicAuthHeader() {
        String credentials = properties.getSecretKey() + ":";
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
