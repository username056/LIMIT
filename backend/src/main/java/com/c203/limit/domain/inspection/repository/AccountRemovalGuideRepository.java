package com.c203.limit.domain.inspection.repository;

import com.c203.limit.domain.inspection.entity.AccountRemovalGuide;
import com.c203.limit.domain.inspection.enums.DeviceType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRemovalGuideRepository
        extends JpaRepository<AccountRemovalGuide, Long> {
    Optional<AccountRemovalGuide> findFirstByDeviceTypeAndManufacturerOrderByTemplateVersionDesc(
            DeviceType deviceType, String manufacturer);
}
