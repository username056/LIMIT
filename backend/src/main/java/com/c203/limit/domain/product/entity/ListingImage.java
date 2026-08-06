package com.c203.limit.domain.product.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 매물 대표·상세 이미지. DDL: listing_image */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "listing_image")
public class ListingImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id")
    private Listing listing;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_type", nullable = false, length = 30)
    private ListingImageType imageType;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "s3_key", nullable = false, length = 500)
    private String s3Key;

    @Column(name = "cdn_url", length = 500)
    private String cdnUrl;

    @Column(name = "mime_type", nullable = false, length = 50)
    private String mimeType;

    @Column(name = "content_sha256", length = 64)
    private String contentSha256;

    @Column(name = "perceptual_hash", length = 16)
    private String perceptualHash;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ListingImage create(
            Listing listing, ListingImageType imageType, String s3Key, String cdnUrl, String mimeType) {
        return create(listing, imageType, 0, s3Key, cdnUrl, mimeType);
    }

    public static ListingImage create(
            Listing listing,
            ListingImageType imageType,
            int displayOrder,
            String s3Key,
            String cdnUrl,
            String mimeType) {
        ListingImage image = new ListingImage();
        image.listing = listing;
        image.imageType = imageType;
        image.displayOrder = displayOrder;
        image.s3Key = s3Key;
        image.cdnUrl = cdnUrl;
        image.mimeType = mimeType;
        image.createdAt = LocalDateTime.now();
        return image;
    }

    public void changeType(ListingImageType imageType) {
        this.imageType = imageType;
    }

    public void changeOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public void recordHashes(String contentSha256, String perceptualHash, LocalDateTime analyzedAt) {
        this.contentSha256 = contentSha256;
        this.perceptualHash = perceptualHash;
        this.analyzedAt = analyzedAt;
    }
}
