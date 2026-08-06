package com.c203.limit.domain.product.repository;

import com.c203.limit.domain.product.entity.Listing;
import com.c203.limit.domain.product.entity.ListingStatus;
import com.c203.limit.domain.product.moderation.entity.ListingModerationStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

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

    /**
     * 선택 기능 체크리스트 재구성처럼 listing_checklist_item 쓰기가 뒤따르는 갱신에서 쓴다. 동시에
     * 들어온 두 PATCH가 같은 매물을 서로 다른 스냅샷 기준으로 갱신하지 않도록 행 잠금을 건다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select listing from Listing listing"
                    + " where listing.id = :id and listing.sellerId = :sellerId"
                    + " and listing.deletedAt is null")
    Optional<Listing> findByIdAndSellerIdAndDeletedAtIsNullForUpdate(
            @Param("id") Long id, @Param("sellerId") Long sellerId);

    @EntityGraph(attributePaths = "category")
    Page<Listing> findBySellerIdAndDeletedAtIsNull(Long sellerId, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "category.parent"})
    Page<Listing> findBySellerIdAndIdNotAndDeletedAtIsNull(
            Long sellerId, Long id, Pageable pageable);

    long countByModerationStatusAndDeletedAtIsNull(ListingModerationStatus moderationStatus);

    @EntityGraph(attributePaths = {"category", "category.parent"})
    Page<Listing> findByDeviceModelIdAndDeletedAtIsNull(Long deviceModelId, Pageable pageable);

    long countByDeviceModelIdAndDeletedAtIsNull(Long deviceModelId);

    @Query(
            """
            SELECT listing.deviceModelId AS deviceModelId, COUNT(listing.id) AS listingCount
              FROM Listing listing
             WHERE listing.deviceModelId IN :modelIds
               AND listing.deletedAt IS NULL
             GROUP BY listing.deviceModelId
            """)
    List<DeviceModelListingCountProjection> countByDeviceModelIds(
            @Param("modelIds") List<Long> modelIds);

    @Query(
            """
            SELECT listing.status AS status, COUNT(listing.id) AS listingCount
              FROM Listing listing
             WHERE listing.deviceModelId = :modelId
               AND listing.deletedAt IS NULL
             GROUP BY listing.status
            """)
    List<ListingStatusCountProjection> countStatusesByDeviceModelId(
            @Param("modelId") Long modelId);

    /** 판매자 공개 프로필에 보여 줄 판매 중 상품 수. */
    long countBySellerIdAndStatusAndDeletedAtIsNull(Long sellerId, ListingStatus status);

    @Query(
            """
            SELECT COUNT(listing)
              FROM Listing listing
             WHERE listing.sellerId = :sellerId
               AND listing.status = com.c203.limit.domain.product.entity.ListingStatus.ON_SALE
               AND listing.moderationStatus IN (
                    com.c203.limit.domain.product.moderation.entity.ListingModerationStatus.NORMAL,
                    com.c203.limit.domain.product.moderation.entity.ListingModerationStatus.WARNING_ACK_REQUIRED)
               AND listing.deletedAt IS NULL
            """)
    long countPublicBySellerId(@Param("sellerId") Long sellerId);

    boolean existsBySellerIdAndTitleAndDeletedAtIsNull(Long sellerId, String title);

    /** 매물 전용(DRAFT) 체크리스트 템플릿에 항목을 이어 붙이기 전, 다른 매물과 공유되지 않는지 확인한다. */
    long countByChecklistTemplateId(Long checklistTemplateId);

    @EntityGraph(attributePaths = "category")
    Page<Listing> findBySellerIdAndStatusAndDeletedAtIsNull(
            Long sellerId, ListingStatus status, Pageable pageable);

    /**
     * 조회수를 DB에서 원자적으로 올린다.
     *
     * <p>엔티티를 읽어 +1 하고 저장하면 동시 조회에서 갱신이 유실된다. 조회는 서비스에서 가장
     * 동시성이 높은 경로라 그 유실이 실제로 발생한다.
     *
     * <p>벌크 갱신이라 {@code @Version}을 건드리지 않는 것도 의도한 바다. 조회수가 버전을 올리면
     * 누군가 상세를 보는 것만으로 판매자의 상품 수정이 낙관적 락 충돌로 실패할 수 있다.
     *
     * <p>공개 상태가 아닌 매물은 조건에서 걸러 0을 반환한다. 숨김·판매 완료·삭제된 매물은
     * 공개 조회수 집계 대상이 아니다.
     *
     * @return 갱신된 행 수. 0이면 공개 매물이 아니었다는 뜻이다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            UPDATE Listing listing
               SET listing.viewCount = listing.viewCount + 1
             WHERE listing.id = :listingId
               AND listing.status = com.c203.limit.domain.product.entity.ListingStatus.ON_SALE
               AND listing.moderationStatus IN (
                    com.c203.limit.domain.product.moderation.entity.ListingModerationStatus.NORMAL,
                    com.c203.limit.domain.product.moderation.entity.ListingModerationStatus.WARNING_ACK_REQUIRED)
               AND listing.deletedAt IS NULL
            """)
    int increaseViewCount(@Param("listingId") Long listingId);

    /** 자동 구매확정 스케줄러가 처리할 대상 ID만 가볍게 조회한다. */
    @Query(
            """
            SELECT listing.id FROM Listing listing
             WHERE listing.status = com.c203.limit.domain.product.entity.ListingStatus.INSPECTING
               AND listing.autoConfirmAt IS NOT NULL
               AND listing.autoConfirmAt < :before
               AND listing.deletedAt IS NULL
             ORDER BY listing.autoConfirmAt ASC
            """)
    List<Long> findAutoConfirmCandidateIds(@Param("before") LocalDateTime before, Pageable pageable);
}
