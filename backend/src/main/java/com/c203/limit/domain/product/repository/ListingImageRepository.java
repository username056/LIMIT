package com.c203.limit.domain.product.repository;

import com.c203.limit.domain.product.entity.ListingImage;
import com.c203.limit.domain.product.entity.ListingImageType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListingImageRepository extends JpaRepository<ListingImage, Long> {
    Optional<ListingImage> findFirstByListingIdAndImageTypeOrderByIdAsc(
            Long listingId, ListingImageType imageType);

    Optional<ListingImage> findByIdAndListingId(Long id, Long listingId);

    List<ListingImage> findAllByListingIdOrderByDisplayOrderAscIdAsc(Long listingId);

    @Query(
            """
            SELECT image
              FROM ListingImage image
              JOIN FETCH image.listing listing
             WHERE listing.sellerId = :sellerId
               AND listing.deletedAt IS NULL
               AND image.id <> :imageId
               AND (image.contentSha256 IS NOT NULL OR image.perceptualHash IS NOT NULL)
             ORDER BY image.createdAt DESC
            """)
    List<ListingImage> findAnalyzedImagesBySellerExcluding(
            @Param("sellerId") Long sellerId,
            @Param("imageId") Long imageId,
            org.springframework.data.domain.Pageable pageable);

    long countByListingId(Long listingId);

    @Query(
            """
            SELECT image.listing.id AS listingId,
                   image.cdnUrl AS cdnUrl,
                   image.s3Key AS s3Key
              FROM ListingImage image
             WHERE image.imageType = :imageType
               AND image.listing.id IN :listingIds
               AND image.id IN (
                    SELECT MIN(candidate.id)
                      FROM ListingImage candidate
                     WHERE candidate.imageType = :imageType
                       AND candidate.listing.id IN :listingIds
                     GROUP BY candidate.listing.id
               )
            """)
    List<ListingThumbnailProjection> findFirstByListingIdsAndImageType(
            @Param("listingIds") List<Long> listingIds,
            @Param("imageType") ListingImageType imageType);
}
