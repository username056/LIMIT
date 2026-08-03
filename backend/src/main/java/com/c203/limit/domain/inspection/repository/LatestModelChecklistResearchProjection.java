package com.c203.limit.domain.inspection.repository;

import com.c203.limit.domain.inspection.enums.ModelChecklistResearchStatus;

public interface LatestModelChecklistResearchProjection {
    Long getDeviceModelId();

    ModelChecklistResearchStatus getStatus();

    Integer getResearchVersion();
}
