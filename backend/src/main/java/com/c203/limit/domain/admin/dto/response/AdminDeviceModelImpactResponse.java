package com.c203.limit.domain.admin.dto.response;

import java.util.Map;

public record AdminDeviceModelImpactResponse(
        long productCount,
        Map<String, Long> productStatusCounts,
        long researchCount,
        long variantCount) {}
