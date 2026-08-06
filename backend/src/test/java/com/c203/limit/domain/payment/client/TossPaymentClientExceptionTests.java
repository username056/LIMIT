package com.c203.limit.domain.payment.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;

class TossPaymentClientExceptionTests {

    @Test
    void ofKeepsTossCodeAndMessageAsExceptionMessage() {
        TossPaymentClientException exception =
                TossPaymentClientException.of(
                        HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청이 올바르지 않습니다.");

        assertThat(exception.getTossCode()).isEqualTo("INVALID_REQUEST");
        assertThat(exception.getTossMessage()).isEqualTo("요청이 올바르지 않습니다.");
        assertThat(exception.getMessage()).isEqualTo("요청이 올바르지 않습니다.");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void clientErrorWithTerminalCodeIsNotRetryable() {
        assertThat(
                        TossPaymentClientException.of(
                                        HttpStatus.BAD_REQUEST,
                                        "ALREADY_PROCESSED_PAYMENT",
                                        "이미 처리된 결제입니다.")
                                .isRetryable())
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "PROVIDER_ERROR",
                "CARD_PROCESSING_ERROR",
                "NOT_AVAILABLE_PAYMENT",
                "NOT_AVAILABLE_BANK"
            })
    void clientErrorWithTransientCodeIsRetryable(String tossCode) {
        assertThat(
                        TossPaymentClientException.of(HttpStatus.BAD_REQUEST, tossCode, "일시적 오류")
                                .isRetryable())
                .isTrue();
    }

    @Test
    void serverErrorIsRetryableRegardlessOfTossCode() {
        assertThat(
                        TossPaymentClientException.of(
                                        HttpStatus.INTERNAL_SERVER_ERROR,
                                        "FAILED_INTERNAL_SYSTEM_PROCESSING",
                                        "내부 처리 실패")
                                .isRetryable())
                .isTrue();
        assertThat(
                        TossPaymentClientException.of(
                                        HttpStatus.SERVICE_UNAVAILABLE, "UNKNOWN", "점검 중")
                                .isRetryable())
                .isTrue();
    }

    @Test
    void networkFailureHasNoStatusOrCodeAndIsRetryable() {
        IOException cause = new IOException("connection reset");
        TossPaymentClientException exception =
                TossPaymentClientException.networkFailure(
                        new ResourceAccessException("I/O error", cause));

        assertThat(exception.getTossCode()).isNull();
        assertThat(exception.getTossMessage()).isEqualTo("Toss 결제 서버와 통신에 실패했습니다.");
        assertThat(exception.getMessage()).isEqualTo("Toss 결제 서버와 통신에 실패했습니다.");
        assertThat(exception.isRetryable()).isTrue();
        assertThat(exception).hasCauseInstanceOf(ResourceAccessException.class);
        assertThat(exception.getCause()).hasCause(cause);
    }
}
