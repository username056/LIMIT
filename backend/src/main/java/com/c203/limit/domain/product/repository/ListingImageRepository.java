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

    @Query(
            """
            SELECT image.listing.id AS listingId, image.cdnUrl AS cdnUrl
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
