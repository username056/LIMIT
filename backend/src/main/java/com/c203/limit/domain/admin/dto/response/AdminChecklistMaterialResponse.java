package com.c203.limit.domain.admin.dto.response;

import com.c203.limit.domain.product.dto.response.EvidenceResponse;
import java.util.List;

public record AdminChecklistMaterialResponse(
        Long checklistItemId,
        String itemCode,
        String name,
        String evidenceType,
        String completionStatus,
        boolean isRequired,
        List<EvidenceResponse> evidence) {}
