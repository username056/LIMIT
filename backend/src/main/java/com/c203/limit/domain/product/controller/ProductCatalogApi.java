package com.c203.limit.domain.product.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.c203.limit.domain.product.dto.response.ChecklistTemplateResponse;
import com.c203.limit.domain.product.dto.response.DeviceCategoryResponse;
import com.c203.limit.domain.product.dto.response.DeviceModelDetailResponse;
import com.c203.limit.domain.product.dto.response.DeviceModelOptionsResponse;
import com.c203.limit.domain.product.dto.response.DeviceModelSummaryResponse;
import com.c203.limit.domain.product.dto.response.HandoverGuideResponse;
import com.c203.limit.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "04. 기기 카탈로그", description = "카테고리·지원 모델·체크리스트 템플릿·판매 준비 가이드 API")
public interface ProductCatalogApi {

    @Operation(operationId = "category01", summary = "기기 카테고리 목록 조회")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "카테고리 목록 조회 성공", content = @Content(array = @ArraySchema(schema = @Schema(implementation = DeviceCategoryResponse.class))))
    @GetMapping(path = "/api/v1/device-categories", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<List<DeviceCategoryResponse>>> getDeviceCategories(
            @RequestParam(required = false) Long parentId,
            @RequestParam(defaultValue = "true") boolean activeOnly
    );

    @Operation(operationId = "model01", summary = "기기 모델 목록 조회")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "기기 모델 목록 조회 성공", content = @Content(array = @ArraySchema(schema = @Schema(implementation = DeviceModelSummaryResponse.class))))
    @GetMapping(path = "/api/v1/device-models", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<List<DeviceModelSummaryResponse>>> getDeviceModels(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long manufacturerId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    );

    @Operation(operationId = "model02", summary = "기기 모델 상세 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "기기 모델 상세 조회 성공", content = @Content(schema = @Schema(implementation = DeviceModelDetailResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "DEVICE_MODEL_NOT_FOUND")
    })
    @GetMapping(path = "/api/v1/device-models/{deviceModelId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<DeviceModelDetailResponse>> getDeviceModel(@PathVariable Long deviceModelId);

    @Operation(operationId = "model03", summary = "모델 판매 옵션 조회", description = "이미 고른 축의 값을 넘기면 그 조건에서 실제로 존재하는 다음 옵션만 돌려줍니다. 각 축의 후보는 자기 축의 선택을 제외한 나머지 조건으로 계산하므로, 앞 단계 선택을 바꿔도 막다른 길이 생기지 않습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "판매 옵션 조회 성공", content = @Content(schema = @Schema(implementation = DeviceModelOptionsResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "INVALID_INPUT_VALUE — 어떤 조합과도 맞지 않는 선택"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "DEVICE_MODEL_NOT_FOUND")
    })
    @GetMapping(path = "/api/v1/device-models/{deviceModelId}/options", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<DeviceModelOptionsResponse>> getDeviceModelOptions(
            @PathVariable Long deviceModelId,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String storageGb,
            @RequestParam(required = false) String memoryGb,
            @RequestParam(required = false) String screenSizeInches,
            @RequestParam(required = false) String cpu,
            @RequestParam(required = false) String gpu,
            @RequestParam(required = false) String connectivity
    );

    @Operation(operationId = "checklist01", summary = "모델 체크리스트 템플릿 조회", description = "상품 등록 시 스냅샷으로 고정될 현재 PUBLISHED 템플릿을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "체크리스트 템플릿 조회 성공", content = @Content(schema = @Schema(implementation = ChecklistTemplateResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "CHECKLIST_TEMPLATE_NOT_FOUND")
    })
    @GetMapping(path = "/api/v1/device-models/{deviceModelId}/checklist-template", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<ChecklistTemplateResponse>> getChecklistTemplate(@PathVariable Long deviceModelId);

    @Operation(operationId = "guide01", summary = "판매 준비 가이드 조회", description = "모델별 계정 제거와 개인정보 초기화 안내를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "판매 준비 가이드 조회 성공", content = @Content(schema = @Schema(implementation = HandoverGuideResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "HANDOVER_GUIDE_NOT_FOUND")
    })
    @GetMapping(path = "/api/v1/device-models/{deviceModelId}/handover-guide", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<HandoverGuideResponse>> getHandoverGuide(@PathVariable Long deviceModelId);
}
