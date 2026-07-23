package com.c203.limit.domain.product.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매물 상태 변경 이력. DDL: listing_status_history. actor_id는 회원 또는 관리자를 가리키는 다형 참조라 FK를 걸지
 * 않는다(시스템 처리 시 NULL).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "listing_status_history")
public class ListingStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id")
    private Listing listing;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 20)
    private ListingStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private ListingStatus toStatus;

    @Column(length = 200)
    private String reason;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ListingStatusHistory record(
            Listing listing, ListingStatus fromStatus, ListingStatus toStatus, String reason, Long actorId) {
        ListingStatusHistory history = new ListingStatusHistory();
        history.listing = listing;
        history.fromStatus = fromStatus;
        history.toStatus = toStatus;
        history.reason = reason;
        history.actorId = actorId;
        history.createdAt = LocalDateTime.now();
        return history;
    }
}
