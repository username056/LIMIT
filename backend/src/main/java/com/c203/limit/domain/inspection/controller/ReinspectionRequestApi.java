package com.c203.limit.domain.inspection.controller;

import com.c203.limit.domain.inspection.dto.request.ReinspectionRequestCreateRequest;
import com.c203.limit.domain.inspection.dto.response.ReinspectionRequestResponse;
import com.c203.limit.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "09. 검수")
public interface ReinspectionRequestApi {

    @Operation(
            operationId = "reinspection01",
            summary = "재검수 요청",
            description = "구매자가 체크리스트 항목별 재검수를 요청하고 커밋 후 판매자 채팅방에 알립니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201", description = "재검수 요청 생성"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403", description = "REINSPECTION_BUYER_REQUIRED")
    })
    @PostMapping("/api/v1/listings/{listingId}/reinspection-requests")
    ResponseEntity<ApiResponse<ReinspectionRequestResponse>> create(
            @PathVariable Long listingId,
            @Valid @RequestBody ReinspectionRequestCreateRequest request);

    @Operation(
            operationId = "reinspection02",
            summary = "재검수 승인 및 완료",
            description = "판매자가 재검수를 완료하고 커밋 후 구매자 채팅방에 알립니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200", description = "재검수 완료"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403", description = "REINSPECTION_SELLER_REQUIRED"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409", description = "REINSPECTION_INVALID_STATE")
    })
    @PostMapping("/api/v1/reinspection-requests/{requestKey}/complete")
    ResponseEntity<ApiResponse<ReinspectionRequestResponse>> complete(
            @PathVariable String requestKey);
}
