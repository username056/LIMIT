package com.c203.limit.domain.product.controller;

import com.c203.limit.domain.product.dto.request.CreateDeviceModelRequest;
import com.c203.limit.domain.product.dto.response.DeviceModelRequestResponse;
import com.c203.limit.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

public interface DeviceModelRequestApi {
    @Operation(
            summary = "기기 모델 직접 입력 및 즉시 등록",
            description = "모델과 기본 체크리스트를 즉시 준비하고 관리자 사후 검토 요청을 함께 생성합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/api/v1/device-model-requests")
    ResponseEntity<ApiResponse<DeviceModelRequestResponse>> createDeviceModelRequest(
            @Valid @RequestBody CreateDeviceModelRequest request);
}
