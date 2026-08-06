package com.c203.limit.domain.payment.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.c203.limit.domain.payment.config.TossPaymentProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class TossPaymentClientTests {

    private static final String CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm";
    private static final String ORDER_URL = "https://api.tosspayments.com/v1/payments/orders/order-1";
    private static final String DUMMY_SECRET = "dummy-toss-secret";
    private static final String API_VERSION = "2022-11-16";
    private static final String EXPECTED_AUTHORIZATION =
            "Basic "
                    + Base64.getEncoder()
                            .encodeToString((DUMMY_SECRET + ":").getBytes(StandardCharsets.UTF_8));

    @Test
    void confirmSendsSignedRequestAndReturnsApprovedPayment() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = client(builder, API_VERSION);

        server.expect(requestTo(CONFIRM_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, EXPECTED_AUTHORIZATION))
                .andExpect(header("Idempotency-Key", "idem-1"))
                .andExpect(header("TossPayments-api-version", API_VERSION))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(
                        content()
                                .json(
                                        """
                                        {"paymentKey":"pay-1","orderId":"order-1","amount":15000}
                                        """))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "paymentKey": "pay-1",
                                  "orderId": "order-1",
                                  "status": "DONE",
                                  "totalAmount": 15000,
                                  "method": "카드",
                                  "approvedAt": "2026-08-06T12:34:56+09:00"
                                }
                                """,
                                MediaType.APPLICATION_JSON));

        TossPaymentResponse response = client.confirm("pay-1", "order-1", 15000L, "idem-1");

        assertThat(response.paymentKey()).isEqualTo("pay-1");
        assertThat(response.orderId()).isEqualTo("order-1");
        assertThat(response.status()).isEqualTo("DONE");
        assertThat(response.totalAmount()).isEqualTo(15000L);
        assertThat(response.method()).isEqualTo("카드");
        assertThat(response.approvedAt()).isNotNull();
        server.verify();
    }

    @Test
    void confirmOmitsApiVersionHeaderWhenPropertyIsBlank() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = client(builder, "   ");

        server.expect(requestTo(CONFIRM_URL))
                .andExpect(headerDoesNotExist("TossPayments-api-version"))
                .andRespond(
                        withSuccess(
                                """
                                {"paymentKey":"pay-1","orderId":"order-1","status":"DONE","totalAmount":15000}
                                """,
                                MediaType.APPLICATION_JSON));

        assertThat(client.confirm("pay-1", "order-1", 15000L, "idem-1").status()).isEqualTo("DONE");
        server.verify();
    }

    @Test
    void confirmTranslatesClientErrorBodyIntoNonRetryableDomainException() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = client(builder, API_VERSION);

        server.expect(requestTo(CONFIRM_URL))
                .andRespond(
                        withStatus(HttpStatus.BAD_REQUEST)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        """
                                        {"code":"ALREADY_PROCESSED_PAYMENT","message":"이미 처리된 결제입니다."}
                                        """));

        assertThatThrownBy(() -> client.confirm("pay-1", "order-1", 15000L, "idem-1"))
                .isInstanceOfSatisfying(
                        TossPaymentClientException.class,
                        exception -> {
                            assertThat(exception.getTossCode())
                                    .isEqualTo("ALREADY_PROCESSED_PAYMENT");
                            assertThat(exception.getTossMessage()).isEqualTo("이미 처리된 결제입니다.");
                            assertThat(exception.getMessage()).isEqualTo("이미 처리된 결제입니다.");
                            assertThat(exception.isRetryable()).isFalse();
                        });
        server.verify();
    }

    @Test
    void confirmMarksTransientProviderErrorAsRetryable() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = client(builder, API_VERSION);

        server.expect(requestTo(CONFIRM_URL))
                .andRespond(
                        withStatus(HttpStatus.BAD_REQUEST)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        """
                                        {"code":"PROVIDER_ERROR","message":"결제사 오류입니다."}
                                        """));

        assertThatThrownBy(() -> client.confirm("pay-1", "order-1", 15000L, "idem-1"))
                .isInstanceOfSatisfying(
                        TossPaymentClientException.class,
                        exception -> assertThat(exception.isRetryable()).isTrue());
        server.verify();
    }

    @Test
    void confirmMarksServerErrorAsRetryable() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = client(builder, API_VERSION);

        server.expect(requestTo(CONFIRM_URL))
                .andRespond(
                        withServerError()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        """
                                        {"code":"FAILED_INTERNAL_SYSTEM_PROCESSING","message":"내부 처리 실패"}
                                        """));

        assertThatThrownBy(() -> client.confirm("pay-1", "order-1", 15000L, "idem-1"))
                .isInstanceOfSatisfying(
                        TossPaymentClientException.class,
                        exception -> {
                            assertThat(exception.getTossCode())
                                    .isEqualTo("FAILED_INTERNAL_SYSTEM_PROCESSING");
                            assertThat(exception.isRetryable()).isTrue();
                        });
        server.verify();
    }

    @Test
    void confirmFallsBackToUnknownCodeWhenErrorBodyIsNotJson() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = client(builder, API_VERSION);

        server.expect(requestTo(CONFIRM_URL))
                .andRespond(
                        withStatus(HttpStatus.BAD_GATEWAY)
                                .contentType(MediaType.TEXT_HTML)
                                .body("<html><body>gateway down</body></html>"));

        assertThatThrownBy(() -> client.confirm("pay-1", "order-1", 15000L, "idem-1"))
                .isInstanceOfSatisfying(
                        TossPaymentClientException.class,
                        exception -> {
                            assertThat(exception.getTossCode()).isEqualTo("UNKNOWN");
                            assertThat(exception.getTossMessage())
                                    .isEqualTo("Toss 응답을 해석할 수 없습니다.");
                            assertThat(exception.isRetryable()).isTrue();
                        });
        server.verify();
    }

    @Test
    void confirmFallsBackToUnknownCodeWhenErrorBodyIsEmpty() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = client(builder, API_VERSION);

        server.expect(requestTo(CONFIRM_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body(""));

        assertThatThrownBy(() -> client.confirm("pay-1", "order-1", 15000L, "idem-1"))
                .isInstanceOfSatisfying(
                        TossPaymentClientException.class,
                        exception -> assertThat(exception.getTossCode()).isEqualTo("UNKNOWN"));
        server.verify();
    }

    @Test
    void confirmKeepsNullCodeWhenErrorBodyOmitsCodeField() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = client(builder, API_VERSION);

        server.expect(requestTo(CONFIRM_URL))
                .andRespond(
                        withStatus(HttpStatus.BAD_REQUEST)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("""
                                {"message":"코드 없는 오류"}
                                """));

        assertThatThrownBy(() -> client.confirm("pay-1", "order-1", 15000L, "idem-1"))
                .isInstanceOfSatisfying(
                        TossPaymentClientException.class,
                        exception -> {
                            assertThat(exception.getTossCode()).isNull();
                            assertThat(exception.getTossMessage()).isEqualTo("코드 없는 오류");
                        });
        server.verify();
    }

    @Test
    void confirmWrapsNetworkFailureIntoRetryableDomainException() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = client(builder, API_VERSION);

        server.expect(requestTo(CONFIRM_URL))
                .andRespond(
                        request -> {
                            throw new SocketTimeoutException("read timed out");
                        });

        assertThatThrownBy(() -> client.confirm("pay-1", "order-1", 15000L, "idem-1"))
                .isInstanceOfSatisfying(
                        TossPaymentClientException.class,
                        exception -> {
                            assertThat(exception.getTossCode()).isNull();
                            assertThat(exception.getTossMessage())
                                    .isEqualTo("Toss 결제 서버와 통신에 실패했습니다.");
                            assertThat(exception.isRetryable()).isTrue();
                            assertThat(exception).hasCauseInstanceOf(ResourceAccessException.class);
                        });
        server.verify();
    }

    @Test
    void findByOrderIdRequestsOrderScopedResourceWithoutApiVersionHeaderWhenNotConfigured() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = client(builder, null);

        server.expect(requestTo(ORDER_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, EXPECTED_AUTHORIZATION))
                .andExpect(headerDoesNotExist("TossPayments-api-version"))
                .andRespond(
                        withSuccess(
                                """
                                {"paymentKey":"pay-1","orderId":"order-1","status":"DONE","totalAmount":15000}
                                """,
                                MediaType.APPLICATION_JSON));

        TossPaymentResponse response = client.findByOrderId("order-1");

        assertThat(response.orderId()).isEqualTo("order-1");
        assertThat(response.totalAmount()).isEqualTo(15000L);
        server.verify();
    }

    @Test
    void findByOrderIdTranslatesNotFoundIntoDomainException() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = client(builder, API_VERSION);

        server.expect(requestTo(ORDER_URL))
                .andRespond(
                        withStatus(HttpStatus.NOT_FOUND)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        """
                                        {"code":"NOT_FOUND_PAYMENT_SESSION","message":"결제 세션을 찾을 수 없습니다."}
                                        """));

        assertThatThrownBy(() -> client.findByOrderId("order-1"))
                .isInstanceOfSatisfying(
                        TossPaymentClientException.class,
                        exception -> {
                            assertThat(exception.getTossCode())
                                    .isEqualTo("NOT_FOUND_PAYMENT_SESSION");
                            assertThat(exception.isRetryable()).isFalse();
                        });
        server.verify();
    }

    @Test
    void findByOrderIdWrapsNetworkFailureIntoDomainException() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = client(builder, API_VERSION);

        server.expect(requestTo(ORDER_URL))
                .andRespond(
                        request -> {
                            throw new SocketTimeoutException("read timed out");
                        });

        assertThatThrownBy(() -> client.findByOrderId("order-1"))
                .isInstanceOfSatisfying(
                        TossPaymentClientException.class,
                        exception -> assertThat(exception.isRetryable()).isTrue());
        server.verify();
    }

    private TossPaymentClient client(RestClient.Builder builder, String apiVersion) {
        return new TossPaymentClient(builder, properties(apiVersion), new ObjectMapper());
    }

    private TossPaymentProperties properties(String apiVersion) {
        TossPaymentProperties properties = new TossPaymentProperties();
        properties.setSecretKey(DUMMY_SECRET);
        properties.setApiVersion(apiVersion);
        return properties;
    }
}
