package com.c203.limit.domain.settlement.entity;

import com.c203.limit.domain.member.entity.Member;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "settlement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "settlement_id")
    private Long id;

    // TODO: listing 도메인 엔티티 생성 후 @ManyToOne(Listing)으로 교체
    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id")
    private Member seller;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private SettlementStatus status = SettlementStatus.PENDING;

    @Column(name = "pending_at", nullable = false)
    private LocalDateTime pendingAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    public static Settlement create(Long listingId, Member seller, int amount) {
        return Settlement.builder()
                .listingId(listingId)
                .seller(seller)
                .amount(amount)
                .pendingAt(LocalDateTime.now())
                .build();
    }

    public void settle() {
        this.status = SettlementStatus.SETTLED;
        this.settledAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = SettlementStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }
}
