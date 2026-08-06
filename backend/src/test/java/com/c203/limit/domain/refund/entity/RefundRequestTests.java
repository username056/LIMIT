package com.c203.limit.domain.refund.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RefundRequestTests {

    /**
     * {@code status}/{@code retryCount}는 {@code @Builder.Default}로만 채워진다. 그 애너테이션이
     * 빠지면 필드 초기화가 조용히 무시돼 상태가 null인 환불 요청이 저장되므로 초기값을 고정해 둔다.
     */
    @Test
    void requestStartsAsRequestedWithoutRetries() {
        RefundRequest refundRequest = RefundRequest.request(null, "검수 결과 불일치", 5L);

        assertThat(refundRequest.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refundRequest.getRetryCount()).isZero();
        assertThat(refundRequest.getReason()).isEqualTo("검수 결과 불일치");
        assertThat(refundRequest.getChecklistItemId()).isEqualTo(5L);
        assertThat(refundRequest.getRequestedAt()).isNotNull();
        assertThat(refundRequest.getProcessedAt()).isNull();
        assertThat(refundRequest.getRefundedAt()).isNull();
        assertThat(refundRequest.getPgRefundTransactionId()).isNull();
    }
}
