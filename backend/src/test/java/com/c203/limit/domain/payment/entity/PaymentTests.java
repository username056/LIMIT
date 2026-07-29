package com.c203.limit.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentTests {

    private Payment requestedPayment() {
        Member buyer = Member.createLocal("buyer@test.com", "encoded", "buyer", null);
        return Payment.request(100L, buyer, "idem-1", BigDecimal.valueOf(650_000), PaymentMethod.CARD);
    }

    @Test
    void expireMovesRequestedPaymentToExpired() {
        Payment payment = requestedPayment();

        payment.expire("예약 만료");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(payment.getFailedReason()).isEqualTo("예약 만료");
    }

    @Test
    void expireRejectsAlreadyApprovedPayment() {
        Payment payment = requestedPayment();
        payment.approve(BigDecimal.valueOf(650_000), "txn-1");

        assertThatThrownBy(() -> payment.expire("예약 만료"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_NOT_EXPIRABLE));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void expireRejectsAlreadyExpiredPayment() {
        Payment payment = requestedPayment();
        payment.expire("최초 만료");

        assertThatThrownBy(() -> payment.expire("중복 만료"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PAYMENT_NOT_EXPIRABLE));
    }
}
