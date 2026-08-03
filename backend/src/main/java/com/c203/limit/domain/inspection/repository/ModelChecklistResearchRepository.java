package com.c203.limit.domain.inspection.repository;

import com.c203.limit.domain.inspection.entity.ModelChecklistResearch;
import com.c203.limit.domain.inspection.enums.ModelChecklistResearchStatus;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ModelChecklistResearchRepository
        extends JpaRepository<ModelChecklistResearch, Long> {

    Optional<ModelChecklistResearch> findByDeviceModelIdAndResearchVersion(
            Long deviceModelId, int researchVersion);

    Optional<ModelChecklistResearch> findFirstByDeviceModelIdOrderByResearchVersionDesc(
            Long deviceModelId);

    List<ModelChecklistResearch> findByStatusOrderByCreatedAtAsc(
            ModelChecklistResearchStatus status);

    List<ModelChecklistResearch> findAllByOrderByCreatedAtDesc();

    @Query(
            """
            SELECT research.deviceModelId AS deviceModelId,
                   research.status AS status,
                   research.researchVersion AS researchVersion
              FROM ModelChecklistResearch research
             WHERE research.deviceModelId IN :modelIds
               AND research.researchVersion = (
                    SELECT MAX(latest.researchVersion)
                      FROM ModelChecklistResearch latest
                     WHERE latest.deviceModelId = research.deviceModelId)
            """)
    List<LatestModelChecklistResearchProjection> findLatestSummaries(
            @Param("modelIds") Set<Long> modelIds);

    Page<ModelChecklistResearch> findByDeviceModelId(Long deviceModelId, Pageable pageable);

    long countByDeviceModelId(Long deviceModelId);

    @Query(
            value =
                    """
                    SELECT research.device_model_id
                      FROM model_checklist_research research
                      JOIN (
                            SELECT device_model_id, MAX(research_version) AS latest_version
                              FROM model_checklist_research
                             GROUP BY device_model_id
                      ) latest
                        ON latest.device_model_id = research.device_model_id
                       AND latest.latest_version = research.research_version
                     WHERE research.status = :status
                    """,
            nativeQuery = true)
    List<Long> findDeviceModelIdsByLatestStatus(@Param("status") String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select research from ModelChecklistResearch research where research.id = :id")
    Optional<ModelChecklistResearch> findByIdForUpdate(@Param("id") Long id);
}
