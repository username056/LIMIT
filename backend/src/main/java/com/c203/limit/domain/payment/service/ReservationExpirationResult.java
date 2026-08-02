package com.c203.limit.domain.payment.service;

/** {@link PaymentReservationExpirationService#expireOne(Long)} 처리 결과. */
public enum ReservationExpirationResult {
    /** 요청 상태 결제를 찾아 만료 처리하고 매물 예약도 해제함. */
    EXPIRED,
    /** 예약은 만료 대상이었지만 대응하는 요청 상태 결제가 없어 아무 것도 하지 않음. */
    SKIPPED_NO_PAYMENT,
    /** confirm 시도 흔적이 있어 만료 전 PG 재확인을 거쳤고, 실제로는 승인된 결제라 복구하고 만료하지 않음. */
    RECOVERED
}
