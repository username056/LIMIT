package com.c203.limit.domain.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.Set;

@Schema(name = "ReviewChecklistResearchRequest", description = "모델 체크리스트 조사 검토 요청")
public record ReviewChecklistResearchRequest(
        Set<String> approvedFeatureCodes, @Size(max = 500) String note) {}
