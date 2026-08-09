package com.c203.limit.domain.inspection.repository;

import com.c203.limit.domain.inspection.entity.ListingChecklistItem;
import com.c203.limit.domain.inspection.enums.ChecklistItemCompletionStatus;
import com.c203.limit.domain.inspection.enums.ChecklistItemOrigin;
import com.c203.limit.domain.inspection.enums.EvidenceType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListingChecklistItemRepository
        extends JpaRepository<ListingChecklistItem, Long> {
    List<ListingChecklistItem> findByListingIdOrderByDisplayOrderAsc(Long listingId);

    List<ListingChecklistItem> findByListingIdAndIsRequiredTrueOrderByDisplayOrderAsc(Long listingId);

    Optional<ListingChecklistItem> findByIdAndListingId(Long id, Long listingId);

    Optional<ListingChecklistItem> findByListingIdAndItemCode(Long listingId, String itemCode);

    List<ListingChecklistItem> findByListingIdAndItemOrigin(
            Long listingId, ChecklistItemOrigin itemOrigin);

    List<ListingChecklistItem> findAllByIdInAndListingId(List<Long> ids, Long listingId);

    /*
      구매자에게 보이는 항목만 센다.
      ---------------------------------------------------------------------------
      이 숫자는 상품 카드와 상세 화면에 '검증 9/10'처럼 나가는 구매자용 값이다. 그런데 필수 항목
      중에는 구매자에게 보이지 않는 것이 있다(계정 로그아웃·초기화 확인 같은 것). 그것까지 세면
      상세 화면의 체크리스트에는 9개가 전부 완료로 떠 있는데 카드만 9/10이 되어, 구매자 입장에서는
      뭐가 하나 빠졌는지 찾을 길이 없다.

      분모를 상세 화면의 '판매글에 등록된 검증 항목' 목록과 같게 맞춘다. 판매자가 숨은 항목을
      건너뛰어도 된다는 뜻은 아니다 — 등록을 막는 것은 이 값이 아니라 Listing.publish의
      precheckCompleted다.
    */
    @Query(
            """
            SELECT item.listingId AS listingId,
                   SUM(CASE WHEN item.isRequired = true AND item.visibleToBuyer = true
                            THEN 1 ELSE 0 END) AS requiredCount,
                   SUM(CASE WHEN item.isRequired = true AND item.visibleToBuyer = true
                             AND item.completionStatus = :completedStatus
                            THEN 1 ELSE 0 END) AS completedRequiredCount,
                   SUM(CASE WHEN item.isRequired = true AND item.visibleToBuyer = true
                             AND item.evidenceType = :confirmationType
                            THEN 1 ELSE 0 END) AS requiredConfirmationCount,
                   SUM(CASE WHEN item.isRequired = true AND item.visibleToBuyer = true
                             AND item.evidenceType = :confirmationType
                             AND item.completionStatus = :completedStatus
                            THEN 1 ELSE 0 END) AS completedConfirmationCount
              FROM ListingChecklistItem item
             WHERE item.listingId IN :listingIds
             GROUP BY item.listingId
            """)
    List<ListingChecklistCountProjection> countRequiredByListingIds(
            @Param("listingIds") List<Long> listingIds,
            @Param("completedStatus") ChecklistItemCompletionStatus completedStatus,
            @Param("confirmationType") EvidenceType confirmationType);

    List<ListingChecklistItem> findAllByListingIdOrderByDisplayOrderAsc(Long listingId);
}
