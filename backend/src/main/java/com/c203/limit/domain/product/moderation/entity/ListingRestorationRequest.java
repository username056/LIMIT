package com.c203.limit.domain.product.moderation.entity;

import com.c203.limit.global.common.BaseTimeEntity;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "listing_restoration_request")
public class ListingRestorationRequest extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "restoration_request_id")
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "request_note", length = 1000)
    private String requestNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RestorationRequestStatus status;

    @Column(name = "reviewer_admin_id")
    private Long reviewerAdminId;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    public static ListingRestorationRequest create(Long listingId, Long sellerId, String requestNote) {
        ListingRestorationRequest request = new ListingRestorationRequest();
        request.listingId = listingId;
        request.sellerId = sellerId;
        request.requestNote = trimToNull(requestNote);
        request.status = RestorationRequestStatus.PENDING;
        return request;
    }

    public void decide(RestorationDecision decision, Long adminId, String note, LocalDateTime now) {
        if (status != RestorationRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.RESTORATION_REQUEST_ALREADY_REVIEWED);
        }
        status = decision == RestorationDecision.APPROVE
                ? RestorationRequestStatus.APPROVED
                : RestorationRequestStatus.REJECTED;
        reviewerAdminId = adminId;
        reviewNote = trimToNull(note);
        reviewedAt = now;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
