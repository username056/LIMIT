package com.c203.limit.domain.product.dto.request;

import com.c203.limit.domain.inspection.checklist.LaptopFeatureCode;
import com.c203.limit.domain.product.entity.OsFamily;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.Set;

@Schema(
        name = "GenerateChecklistRequest",
        description = "노트북 체크리스트 생성 요청. 카탈로그 모델 ID 또는 직접 입력 정보를 사용합니다.")
public record GenerateChecklistRequest(
        @Schema(description = "등록된 기기 모델 ID", example = "201") Long deviceModelId,
        @Schema(description = "직접 입력 제조사", example = "Samsung")
                @Size(max = 50)
                String manufacturer,
        @Schema(description = "직접 입력 모델명", example = "Galaxy Book4 Pro")
                @Size(max = 100)
                String modelName,
        @Schema(description = "직접 입력 모델 코드", example = "NT960XGK-KC51G")
                @Size(max = 50)
                String modelCode,
        @Schema(description = "직접 입력 운영체제", allowableValues = {"WINDOWS", "LINUX"})
                OsFamily osFamily,
        @Schema(description = "판매자가 확인한 지원 기능. 최대 5개")
                @Size(max = 5)
                Set<LaptopFeatureCode> confirmedFeatures) {}
