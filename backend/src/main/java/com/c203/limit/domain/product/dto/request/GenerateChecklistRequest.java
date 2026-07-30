package com.c203.limit.domain.product.dto.request;

import com.c203.limit.domain.product.entity.OsFamily;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

@Schema(
        name = "GenerateChecklistRequest",
        description = "기기 체크리스트 생성 요청. 카탈로그 모델 ID를 사용하며 직접 입력은 노트북을 지원합니다.")
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
        @Schema(description = "판매자가 확인한 모델별 지원 기능 코드. 최대 5개")
                @Size(max = 5)
                Set<@NotBlank String> confirmedFeatures) {}
