package com.c203.limit.seller.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Generated from the exported DTO specification. */
@Getter
@RequiredArgsConstructor
@Schema(name = "SellerApplicationDocumentResponse", description = "판매자 증빙 문서 정보")
public class SellerApplicationDocumentResponse {

    @Schema(description = "증빙 문서 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Long documentId;

    @Schema(description = "판매자 신청 ID", example = "1", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final Long applicationId;

    @Schema(description = "문서 유형", example = "PASSPORT", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String documentType;

    @Schema(description = "원본 파일명", example = "passport.pdf", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String originalFilename;

    @Schema(description = "MIME 형식", example = "application/pdf", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final String contentType;

    @Schema(description = "파일 크기(Byte)", example = "102400", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private final long fileSize;

    @Schema(description = "업로드 시각", example = "2026-07-16T12:50:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private final OffsetDateTime uploadedAt;
}
