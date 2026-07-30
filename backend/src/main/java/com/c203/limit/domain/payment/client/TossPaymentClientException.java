package com.c203.limit.domain.payment.client;

import java.util.Set;
import org.springframework.http.HttpStatusCode;

/**
 * Toss 결제 API 호출 실패를 표현한다. httpStatus가 null이면 응답 자체를 못 받은 네트워크·타임아웃
 * 오류다. {@link #isRetryable()}은 이 프로젝트가 확정한 정책(5xx 또는 일부 4xx 코드는 일시적
 * 오류로 보고 조회 후 재시도, 그 외 4xx는 종결 실패)을 그대로 구현한다.
 */
public class TossPaymentClientException extends RuntimeException {

    private static final Set<String> RETRYABLE_CODES =
            Set.of("PROVIDER_ERROR", "CARD_PROCESSING_ERROR", "NOT_AVAILABLE_PAYMENT", "NOT_AVAILABLE_BANK");

    private final HttpStatusCode httpStatus;
    private final String tossCode;
    private final String tossMessage;

    private TossPaymentClientException(HttpStatusCode httpStatus, String tossCode, String tossMessage) {
        super(tossMessage);
        this.httpStatus = httpStatus;
        this.tossCode = tossCode;
        this.tossMessage = tossMessage;
    }

    static TossPaymentClientException of(HttpStatusCode httpStatus, String tossCode, String tossMessage) {
        return new TossPaymentClientException(httpStatus, tossCode, tossMessage);
    }

    static TossPaymentClientException networkFailure(Throwable cause) {
        TossPaymentClientException exception =
                new TossPaymentClientException(null, null, "Toss 결제 서버와 통신에 실패했습니다.");
        exception.initCause(cause);
        return exception;
    }

    public boolean isRetryable() {
        if (httpStatus == null) {
            return true;
        }
        if (httpStatus.is5xxServerError()) {
            return true;
        }
        return RETRYABLE_CODES.contains(tossCode);
    }

    public String getTossCode() {
        return tossCode;
    }

    public String getTossMessage() {
        return tossMessage;
    }
}
