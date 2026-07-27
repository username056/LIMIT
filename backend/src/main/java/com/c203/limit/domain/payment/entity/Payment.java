package com.c203.limit.domain.payment.entity;

import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
        name = "payment",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "UK_PAYMENT_IDEMPOTENCY_KEY",
                    columnNames = "idempotency_key"),
            @UniqueConstraint(name = "uk_payment_pg_event_id", columnNames = "pg_event_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    // TODO: listing 도메인 엔티티 생성 후 @ManyToOne(Listing)으로 교체
    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id")
    private Member buyer;

    @Version
    @Column(name = "payment_version", nullable = false)
    @Builder.Default
    private Integer paymentVersion = 1;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "attempt_no", nullable = false)
    @Builder.Default
    private Integer attemptNo = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "pg_provider", nullable = false, length = 20)
    @Builder.Default
    private PgProvider pgProvider = PgProvider.TOSS;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PaymentMethod method;

    @Column(name = "provider_transaction_id", length = 200)
    private String providerTransactionId;

    // PG 웹훅 이벤트 고유 식별자. unique 제약으로 동일 이벤트 중복 처리를 막는다.
    @Column(name = "pg_event_id", length = 200)
    private String pgEventId;

    @Column(name = "webhook_verified", nullable = false)
    @Builder.Default
    private boolean webhookVerified = false;

    @Column(name = "requested_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "approved_amount", precision = 12, scale = 2)
    private BigDecimal approvedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.REQUESTED;

    @Column(name = "failed_reason", length = 255)
    private String failedReason;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    public static Payment request(
            Long listingId,
            Member buyer,
            String idempotencyKey,
            BigDecimal requestedAmount,
            PaymentMethod method) {
        return Payment.builder()
                .listingId(listingId)
                .buyer(buyer)
                .idempotencyKey(idempotencyKey)
                .requestedAmount(requestedAmount)
                .method(method)
                .requestedAt(LocalDateTime.now())
                .build();
    }

    public void approve(BigDecimal approvedAmount, String providerTransactionId) {
        this.status = PaymentStatus.APPROVED;
        this.approvedAmount = approvedAmount;
        this.providerTransactionId = providerTransactionId;
        this.approvedAt = LocalDateTime.now();
    }

    public void fail(String failedReason) {
        this.status = PaymentStatus.FAILED;
        this.failedReason = failedReason;
    }

    public void markWebhookVerified() {
        this.webhookVerified = true;
    }

    public void retry() {
        this.attemptNo += 1;
        this.status = PaymentStatus.REQUESTED;
        this.requestedAt = LocalDateTime.now();
    }

    public void requestRefund() {
        this.status = PaymentStatus.REFUND_REQUESTED;
    }

    public void completeRefund() {
        this.status = PaymentStatus.REFUNDED;
    }
}
