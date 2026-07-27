package com.c203.limit.domain.inspection.dto.request;

import com.c203.limit.domain.inspection.enums.OcrFieldType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "ExtractOcrTextRequest", description = "증거 이미지 OCR 텍스트 추출 요청")
public class ExtractOcrTextRequest {

    @Schema(description = "인식 대상 필드 종류", example = "MODEL_NAME")
    @NotNull
    private final OcrFieldType fieldType;
}
