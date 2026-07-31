package com.c203.limit.domain.product.repository;

import com.c203.limit.domain.product.entity.Listing;
import java.util.Optional;
import com.c203.limit.domain.product.entity.ListingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;

public interface ListingRepository
        extends JpaRepository<Listing, Long>, JpaSpecificationExecutor<Listing> {
    @Override
    @EntityGraph(attributePaths = {"category", "category.parent"})
    Page<Listing> findAll(Specification<Listing> specification, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Optional<Listing> findByIdAndDeletedAtIsNull(Long id);

    @EntityGraph(attributePaths = {"category", "category.parent"})
    Optional<Listing> findByIdAndStatusAndDeletedAtIsNull(Long id, ListingStatus status);

    @EntityGraph(attributePaths = {"category", "category.parent"})
    Optional<Listing> findByIdAndSellerIdAndDeletedAtIsNull(Long id, Long sellerId);

    @EntityGraph(attributePaths = "category")
    Page<Listing> findBySellerIdAndDeletedAtIsNull(Long sellerId, Pageable pageable);

    /** 판매자 공개 프로필에 보여 줄 판매 중 상품 수. */
    long countBySellerIdAndStatusAndDeletedAtIsNull(Long sellerId, ListingStatus status);

    boolean existsBySellerIdAndTitleAndDeletedAtIsNull(Long sellerId, String title);

    @EntityGraph(attributePaths = "category")
    Page<Listing> findBySellerIdAndStatusAndDeletedAtIsNull(
            Long sellerId, ListingStatus status, Pageable pageable);
}
