package com.c203.limit.domain.payment.dto.response;

/** {@link com.c203.limit.domain.payment.service.PaymentService#reconcile(Long)} 처리 결과. */
public enum PaymentReconcileOutcome {
    /** PG 조회 결과 승인이 확인되어 로컬 결제·매물 상태를 복구함. */
    RECOVERED,
    /** 이미 REQUESTED가 아니거나, PG 조회 결과 승인된 기록이 없어 상태를 바꾸지 않음. */
    NO_ACTION
}
