package com.c203.limit.domain.inspection.entity;

import com.c203.limit.domain.inspection.enums.ReinspectionStatus;
import com.c203.limit.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 구매자의 매물 재검수(재촬영) 요청. DDL: reinspection_request. chat_room_id/buyer_id/seller_id는 채팅·회원
 * 도메인 참조라 FK 없이 ID만 보관한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "reinspection_request")
public class ReinspectionRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "request_key", nullable = false, length = 36)
    private String requestKey;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReinspectionStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    public static ReinspectionRequest request(
            Long listingId, Long chatRoomId, String requestKey, String reason, Long buyerId, Long sellerId) {
        ReinspectionRequest request = new ReinspectionRequest();
        request.listingId = listingId;
        request.chatRoomId = chatRoomId;
        request.requestKey = requestKey;
        request.reason = reason;
        request.status = ReinspectionStatus.REQUESTED;
        request.requestedAt = LocalDateTime.now();
        request.buyerId = buyerId;
        request.sellerId = sellerId;
        return request;
    }

    public void complete() {
        this.status = ReinspectionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = ReinspectionStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }
}
