package com.c203.limit.domain.product.repository;

import com.c203.limit.domain.product.entity.Wishlist;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    Optional<Wishlist> findByUserIdAndListingId(Long userId, Long listingId);

    boolean existsByUserIdAndListingId(Long userId, Long listingId);

    @Modifying
    @Query(
            value =
                    """
                    INSERT IGNORE INTO wishlist (user_id, listing_id, created_at)
                    VALUES (:userId, :listingId, CURRENT_TIMESTAMP(6))
                    """,
            nativeQuery = true)
    int insertIgnore(
            @Param("userId") Long userId,
            @Param("listingId") Long listingId);

    long deleteByUserIdAndListingId(Long userId, Long listingId);

    @Query(
            value =
                    """
                    SELECT wishlist
                      FROM Wishlist wishlist
                      JOIN FETCH wishlist.listing listing
                      JOIN FETCH listing.category
                     WHERE wishlist.userId = :userId
                       AND listing.deletedAt IS NULL
                       AND listing.status = com.c203.limit.domain.product.entity.ListingStatus.ON_SALE
                    """,
            countQuery =
                    """
                    SELECT COUNT(wishlist)
                      FROM Wishlist wishlist
                      JOIN wishlist.listing listing
                     WHERE wishlist.userId = :userId
                       AND listing.deletedAt IS NULL
                       AND listing.status = com.c203.limit.domain.product.entity.ListingStatus.ON_SALE
                    """)
    Page<Wishlist> findActiveByUserId(@Param("userId") Long userId, Pageable pageable);
}
