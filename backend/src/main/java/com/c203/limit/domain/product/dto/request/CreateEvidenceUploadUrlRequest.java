package com.c203.limit.domain.product.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Schema(name = "CreateEvidenceUploadUrlRequest", description = "체크리스트 증거 업로드 URL 발급 요청")
public class CreateEvidenceUploadUrlRequest {

    @Schema(description = "원본 파일명", example = "touch-test.mp4")
    @NotBlank
    private final String filename;

    @Schema(description = "파일 MIME 타입", example = "video/mp4")
    @NotBlank
    private final String contentType;

    @Schema(description = "파일 크기(byte)", example = "10485760")
    @NotNull
    @Positive
    private final Long fileSize;

    @Schema(description = "영상 길이(초). 사진·진단 파일은 null", example = "15")
    @PositiveOrZero
    private final Integer durationSeconds;
}
