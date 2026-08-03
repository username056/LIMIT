package com.c203.limit.domain.inspection.repository;

import com.c203.limit.domain.inspection.entity.ModelChecklistResearch;
import com.c203.limit.domain.inspection.enums.ModelChecklistResearchStatus;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select research from ModelChecklistResearch research where research.id = :id")
    Optional<ModelChecklistResearch> findByIdForUpdate(@Param("id") Long id);
}
