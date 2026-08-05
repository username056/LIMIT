package com.c203.limit.domain.product.dto.response;

import com.c203.limit.domain.inspection.enums.DeviceCheckResult;
import com.c203.limit.domain.inspection.enums.TestType;
import java.util.Map;

public record ProductDraftProgressResponse(
        int step,
        Map<Long, DeviceCheckResult> results,
        Map<TestType, DeviceCheckResult> deviceResults,
        Map<TestType, DeviceCheckResult> automaticDeviceResults) {

    public ProductDraftProgressResponse(int step, Map<Long, DeviceCheckResult> results) {
        this(step, results, Map.of(), Map.of());
    }
}
