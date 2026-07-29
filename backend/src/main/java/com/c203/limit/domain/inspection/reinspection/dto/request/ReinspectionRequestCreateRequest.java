package com.c203.limit.domain.inspection.reinspection.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "ReinspectionRequestCreateRequest", description = "재검수 요청 생성 요청")
public class ReinspectionRequestCreateRequest {

    @Schema(example = "제품 상태를 조금 더 자세히 확인하고 싶습니다.")
    @NotBlank
    @Size(max = 1000)
    private final String reason;

    @Schema(description = "재검수를 요청할 체크리스트 항목 목록 (최소 1개)")
    @NotEmpty
    @Valid
    private final List<ReinspectionRequestItemCreateRequest> items;
}
