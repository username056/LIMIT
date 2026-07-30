package com.c203.limit.domain.product.controller;

import com.c203.limit.domain.product.dto.request.GenerateChecklistRequest;
import com.c203.limit.domain.product.dto.response.ChecklistGenerationApiResponse;
import com.c203.limit.domain.product.dto.response.ChecklistGenerationResponse;
import com.c203.limit.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "03. 상품", description = "중고 전자기기 상품 CRUD와 거래 상태 API")
public interface ChecklistGenerationApi {

    @Operation(
            operationId = "productChecklist01",
            summary = "기기 모델 체크리스트 생성",
            description =
                    "스마트폰·폴더블·태블릿·노트북의 검증된 기본 체크리스트를 생성하고, 공식 제조사 자료 기반 AI 기능 후보를 함께 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "체크리스트 생성 성공",
                content =
                        @Content(
                                schema =
                                        @Schema(
                                                implementation =
                                                        ChecklistGenerationApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "CHECKLIST_OS_NOT_SUPPORTED"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "DEVICE_MODEL_NOT_FOUND")
    })
    @PostMapping(
            path = "/api/v1/checklist-generations",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<ChecklistGenerationResponse>> generateChecklist(
            @Valid @RequestBody GenerateChecklistRequest request);
}
