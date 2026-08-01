package com.c203.limit.domain.product.repository;

import com.c203.limit.domain.inspection.enums.DeviceType;
import com.c203.limit.domain.product.entity.DeviceCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceCategoryRepository extends JpaRepository<DeviceCategory, Long> {

    List<DeviceCategory> findByIsActiveTrueOrderByDisplayOrderAsc();

    List<DeviceCategory> findAllByOrderByDisplayOrderAsc();

    Optional<DeviceCategory> findByCode(DeviceType code);
}
