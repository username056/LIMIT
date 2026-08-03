package com.c203.limit.domain.product.repository;

import com.c203.limit.domain.product.entity.DeviceModel;
import com.c203.limit.domain.product.entity.DeviceModelReviewStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceModelRepository extends JpaRepository<DeviceModel, Long> {

    @EntityGraph(attributePaths = {"category", "manufacturer"})
    Optional<DeviceModel> findWithCatalogById(Long modelId);

    /**
     * 모델 검색. 파라미터 구성은 기존 {@code CategoryRepository.findModels}와 맞췄다 — 두 구조를
     * 병행하는 동안 호출부가 같은 조건으로 양쪽을 조회할 수 있어야 전환 시 결과 차이를 비교할 수 있다.
     *
     * <p>선택 가능한 variant가 하나도 없는 모델은 제외한다. 등록 화면에서 고를 수 있는 조합이
     * 없는 모델을 검색 결과에 노출하면 사용자가 막다른 길로 들어간다.
     */
    @EntityGraph(attributePaths = {"category", "manufacturer"})
    @Query(
            """
            SELECT model
              FROM DeviceModel model
             WHERE model.isActive = true
               AND (:categoryId IS NULL OR model.category.id = :categoryId OR model.id = :categoryId)
               AND (:manufacturerId IS NULL OR model.manufacturer.id = :manufacturerId)
               AND (:keyword IS NULL
                    OR LOWER(model.modelName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR model.normalizedModelName LIKE CONCAT('%', :normalizedKeyword, '%')
                    OR LOWER(model.modelCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(model.manufacturer.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
               AND EXISTS (
                    SELECT 1 FROM DeviceVariant variant
                     WHERE variant.model = model AND variant.isActive = true)
             ORDER BY model.displayOrder ASC, model.id ASC
            """)
    Page<DeviceModel> search(
            @Param("categoryId") Long categoryId,
            @Param("manufacturerId") Long manufacturerId,
            @Param("keyword") String keyword,
            @Param("normalizedKeyword") String normalizedKeyword,
            Pageable pageable);

    boolean existsByManufacturerIdAndModelCode(Long manufacturerId, String modelCode);

    boolean existsByManufacturerIdAndModelCodeAndIdNot(
            Long manufacturerId, String modelCode, Long modelId);

    @EntityGraph(attributePaths = {"category", "manufacturer"})
    List<DeviceModel> findByReviewStatusOrderByCreatedAtDesc(DeviceModelReviewStatus reviewStatus);

    @EntityGraph(attributePaths = {"category", "manufacturer"})
    List<DeviceModel> findAllByOrderByCreatedAtDesc();

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select model from DeviceModel model where model.id = :modelId")
    Optional<DeviceModel> findByIdForUpdate(@Param("modelId") Long modelId);
}
