package com.c203.limit.domain.inspection.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "listing_account_removal_check")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListingAccountRemovalCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "listing_account_removal_check_id")
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guide_id", nullable = false)
    private AccountRemovalGuide guide;

    @Column(name = "seller_confirmed", nullable = false)
    private boolean sellerConfirmed;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "screenshot_url", length = 500)
    private String screenshotUrl;

    @Builder
    private ListingAccountRemovalCheck(Long listingId, AccountRemovalGuide guide, boolean sellerConfirmed,
                                       LocalDateTime confirmedAt, String screenshotUrl) {
        this.listingId = listingId;
        this.guide = guide;
        this.sellerConfirmed = sellerConfirmed;
        this.confirmedAt = confirmedAt;
        this.screenshotUrl = screenshotUrl;
    }

    /** 판매자가 가이드를 확인하고 체크했을 때 호출. */
    public void confirm(String screenshotUrl) {
        this.sellerConfirmed = true;
        this.confirmedAt = LocalDateTime.now();
        this.screenshotUrl = screenshotUrl;
    }
}
