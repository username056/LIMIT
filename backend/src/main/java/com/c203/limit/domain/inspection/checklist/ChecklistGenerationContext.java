package com.c203.limit.domain.inspection.checklist;

import com.c203.limit.domain.product.entity.OsFamily;
import java.util.Set;

public record ChecklistGenerationContext(
        Long deviceModelId,
        String manufacturer,
        String modelName,
        String modelCode,
        OsFamily osFamily,
        Set<LaptopFeatureCode> confirmedFeatures) {

    public ChecklistGenerationContext {
        confirmedFeatures =
                confirmedFeatures == null ? Set.of() : Set.copyOf(confirmedFeatures);
    }
}
