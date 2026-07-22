package com.c203.limit.domain.refund.entity;

import com.c203.limit.domain.admin.entity.AdminAccount;
import com.c203.limit.domain.payment.entity.Payment;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "refund_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class RefundRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refund_request_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(nullable = false, length = 500)
    private String reason;

    // TODO: listing_checklist_item 도메인 엔티티 생성 후 @ManyToOne으로 교체
    @Column(name = "checklist_item_id")
    private Long checklistItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private RefundStatus status = RefundStatus.REQUESTED;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_admin_id")
    private AdminAccount processedAdmin;

    @Column(name = "pg_refund_transaction_id", length = 200)
    private String pgRefundTransactionId;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    public static RefundRequest request(Payment payment, String reason, Long checklistItemId) {
        return RefundRequest.builder()
                .payment(payment)
                .reason(reason)
                .checklistItemId(checklistItemId)
                .requestedAt(LocalDateTime.now())
                .build();
    }

    public void approve(AdminAccount admin) {
        this.status = RefundStatus.APPROVED;
        this.processedAdmin = admin;
        this.processedAt = LocalDateTime.now();
    }

    public void reject(AdminAccount admin, String rejectReason) {
        this.status = RefundStatus.REJECTED;
        this.processedAdmin = admin;
        this.rejectReason = rejectReason;
        this.processedAt = LocalDateTime.now();
    }

    public void completeProcessing(String pgRefundTransactionId) {
        this.status = RefundStatus.PROCESSED;
        this.pgRefundTransactionId = pgRefundTransactionId;
        this.refundedAt = LocalDateTime.now();
    }
}
