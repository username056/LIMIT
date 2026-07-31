package com.c203.limit.domain.product.dto.response;

import com.c203.limit.domain.inspection.enums.DeviceCheckResult;
import java.util.Map;

public record ProductDraftProgressResponse(int step, Map<Long, DeviceCheckResult> results) {}
