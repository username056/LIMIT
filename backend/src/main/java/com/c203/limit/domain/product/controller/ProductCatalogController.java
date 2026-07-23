package com.c203.limit.domain.product.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.c203.limit.domain.product.dto.response.ChecklistTemplateItemResponse;
import com.c203.limit.domain.product.dto.response.ChecklistTemplateResponse;
import com.c203.limit.domain.product.dto.response.DeviceCategoryResponse;
import com.c203.limit.domain.product.dto.response.DeviceModelDetailResponse;
import com.c203.limit.domain.product.dto.response.DeviceModelSummaryResponse;
import com.c203.limit.domain.product.dto.response.HandoverGuideResponse;
import com.c203.limit.domain.product.dto.response.HandoverGuideStepResponse;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.response.PageMetaResponse;

@RestController
public class ProductCatalogController implements ProductCatalogApi {

    @Override
    public ResponseEntity<ApiResponse<List<DeviceCategoryResponse>>> getDeviceCategories(
            Long parentId,
            boolean activeOnly
    ) {
        List<DeviceCategoryResponse> categories = List.of(
                new DeviceCategoryResponse(1L, "SMARTPHONE_BAR", "일반형 스마트폰", null, true),
                new DeviceCategoryResponse(2L, "SMARTPHONE_FOLDABLE", "폴더블 스마트폰", null, true),
                new DeviceCategoryResponse(3L, "TABLET", "태블릿", null, true),
                new DeviceCategoryResponse(4L, "LAPTOP_WINDOWS", "Windows 노트북", null, true)
        );
        return ResponseEntity.ok(ApiResponse.ok(categories));
    }

    @Override
    public ResponseEntity<ApiResponse<List<DeviceModelSummaryResponse>>> getDeviceModels(
            Long categoryId,
            Long manufacturerId,
            String keyword,
            int page,
            int size
    ) {
        DeviceModelSummaryResponse model = new DeviceModelSummaryResponse(
                101L, 1L, "Samsung", 1L, "SM-S921N", "Galaxy S24", "ANDROID", true
        );
        return ResponseEntity.ok(new ApiResponse<>(
                List.of(model), new PageMetaResponse(page, size, 1, 1, false)
        ));
    }

    @Override
    public ResponseEntity<ApiResponse<DeviceModelDetailResponse>> getDeviceModel(Long deviceModelId) {
        DeviceModelDetailResponse response = new DeviceModelDetailResponse(
                deviceModelId,
                "Samsung",
                "일반형 스마트폰",
                "SM-S921N",
                "Galaxy S24",
                "ANDROID",
                List.of(128, 256, 512),
                1,
                true
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Override
    public ResponseEntity<ApiResponse<ChecklistTemplateResponse>> getChecklistTemplate(Long deviceModelId) {
        List<ChecklistTemplateItemResponse> items = List.of(
                new ChecklistTemplateItemResponse(
                        "SP-EXT-001", "전면·후면·측면 외관", "PHOTO", true,
                        6, 6, null, null, "케이스를 제거하고 기기의 6면을 촬영하세요.", true, true, false
                ),
                new ChecklistTemplateItemResponse(
                        "SP-DSP-002", "화면 전체 터치", "VIDEO", true,
                        null, null, 15, 60, "화면 전체 격자를 끊김 없이 드래그하세요.", true, true, false
                ),
                new ChecklistTemplateItemResponse(
                        "SP-PRV-001", "Samsung·Google 계정 제거", "SELLER_CONFIRMATION", true,
                        null, null, null, null, "계정 제거 후 완료 여부를 확인하세요.", true, false, true
                )
        );
        return ResponseEntity.ok(ApiResponse.ok(new ChecklistTemplateResponse(501L, deviceModelId, 1, items)));
    }

    @Override
    public ResponseEntity<ApiResponse<HandoverGuideResponse>> getHandoverGuide(Long deviceModelId) {
        List<HandoverGuideStepResponse> steps = List.of(
                new HandoverGuideStepResponse(1, "BACKUP_DATA", "개인정보 백업", "필요한 사진·연락처·메시지를 백업하세요.", true),
                new HandoverGuideStepResponse(2, "REMOVE_GOOGLE_ACCOUNT", "Google 계정 제거", "설정의 계정 관리에서 Google 계정을 제거하세요.", true),
                new HandoverGuideStepResponse(3, "REMOVE_SAMSUNG_ACCOUNT", "Samsung 계정 제거", "설정의 계정 관리에서 Samsung 계정을 로그아웃하세요.", true),
                new HandoverGuideStepResponse(4, "FACTORY_RESET", "공장 초기화", "계정을 제거한 뒤 공장 데이터 초기화를 실행하세요.", true)
        );
        HandoverGuideResponse response = new HandoverGuideResponse(
                601L,
                deviceModelId,
                1,
                "Galaxy S24 판매 준비",
                steps,
                "서비스는 개인정보의 완전한 삭제를 보증하지 않습니다."
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
