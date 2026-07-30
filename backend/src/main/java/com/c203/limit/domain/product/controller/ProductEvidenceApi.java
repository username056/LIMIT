package com.c203.limit.domain.product.controller;

import com.c203.limit.domain.product.dto.request.CompleteEvidenceRequest;
import com.c203.limit.domain.product.dto.request.CreateEvidenceUploadUrlRequest;
import com.c203.limit.domain.product.dto.response.EvidenceResponse;
import com.c203.limit.domain.product.dto.response.EvidenceUploadUrlResponse;
import com.c203.limit.domain.product.dto.response.ProductChecklistItemResponse;
import com.c203.limit.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "05. 상품 검증", description = "상품 체크리스트와 S3 증빙 API")
public interface ProductEvidenceApi {

    @Operation(
            operationId = "checklist02",
            summary = "상품 체크리스트 조회",
            description = "상품에 고정된 체크리스트 스냅샷을 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "상품 체크리스트 조회 성공",
            content =
                    @Content(
                            array =
                                    @ArraySchema(
                                            schema =
                                                    @Schema(
                                                            implementation =
                                                                    ProductChecklistItemResponse
                                                                            .class))))
    @GetMapping(
            path = "/api/v1/products/{productId}/checklist-items",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<List<ProductChecklistItemResponse>>> getProductChecklist(
            @PathVariable Long productId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean requiredOnly);

    @Operation(
            operationId = "evidence01",
            summary = "증빙 업로드 URL 발급",
            description = "증빙 유형, MIME, 용량과 영상 길이를 검증하고 Presigned URL을 발급합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "업로드 URL 발급 성공",
                content =
                        @Content(
                                schema =
                                        @Schema(
                                                implementation =
                                                        EvidenceUploadUrlResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "422",
                description = "EVIDENCE_TYPE_MISMATCH")
    })
    @PostMapping(
            path = "/api/v1/products/{productId}/checklist-items/{checklistItemId}/upload-urls",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<EvidenceUploadUrlResponse>> createEvidenceUploadUrl(
            @PathVariable Long productId,
            @PathVariable Long checklistItemId,
            @Valid @RequestBody CreateEvidenceUploadUrlRequest request);

    @Operation(
            operationId = "evidence02",
            summary = "증빙 등록 완료",
            description = "S3 업로드를 확인하고 증빙 이력과 체크리스트 완료 상태를 저장합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "증빙 등록 완료",
                content =
                        @Content(
                                schema = @Schema(implementation = EvidenceResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "DUPLICATE_UPLOAD_COMPLETION")
    })
    @PostMapping(
            path =
                    "/api/v1/products/{productId}/checklist-items/{checklistItemId}/evidence",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<EvidenceResponse>> completeEvidence(
            @PathVariable Long productId,
            @PathVariable Long checklistItemId,
            @Valid @RequestBody CompleteEvidenceRequest request);

    @Operation(operationId = "evidence03", summary = "항목별 증빙 이력 조회")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "증빙 이력 조회 성공",
            content =
                    @Content(
                            array =
                                    @ArraySchema(
                                            schema =
                                                    @Schema(
                                                            implementation =
                                                                    EvidenceResponse.class))))
    @GetMapping(
            path =
                    "/api/v1/products/{productId}/checklist-items/{checklistItemId}/evidence",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<List<EvidenceResponse>>> getEvidenceHistory(
            @PathVariable Long productId, @PathVariable Long checklistItemId);
}
