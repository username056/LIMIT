package com.c203.limit.domain.product.repository;

import com.c203.limit.domain.product.entity.DeviceModelRequest;
import com.c203.limit.domain.product.entity.DeviceModelRequestStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceModelRequestRepository extends JpaRepository<DeviceModelRequest, Long> {
    boolean existsByParentCategoryIdAndManufacturerIgnoreCaseAndModelNameIgnoreCaseAndStatus(
            Long parentCategoryId,
            String manufacturer,
            String modelName,
            DeviceModelRequestStatus status);

    List<DeviceModelRequest> findByStatusOrderByCreatedAtAsc(DeviceModelRequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from DeviceModelRequest request where request.id = :id")
    Optional<DeviceModelRequest> findByIdForUpdate(@Param("id") Long id);
}
