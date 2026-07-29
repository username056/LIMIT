package com.c203.limit.domain.inspection.reinspection.controller;

import com.c203.limit.domain.inspection.reinspection.dto.request.ReinspectionRequestCreateRequest;
import com.c203.limit.domain.inspection.reinspection.dto.response.ReinspectionRequestResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "09. 검수", description = "검수 증거 OCR·DxDiag·배터리 리포트 파싱 API")
public interface ReinspectionRequestApi {

    @Operation(
            operationId = "reinspection01",
            summary = "재검수 요청",
            description =
                    "구매자가 매물의 거래 채팅방을 통해 재검수(재촬영)를 요청합니다. 본인 매물에는 요청할 수"
                            + " 없고, 거래 채팅방이 없으면 새로 생성하며, 선택한 체크리스트 항목이 해당 매물"
                            + " 소속인지 검증한 뒤 REQUESTED 상태로 생성하고 알림 이벤트를 발행합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "요청 생성 완료",
                content = @Content(schema = @Schema(implementation = ReinspectionRequestResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "REINSPECTION_SELF_REQUEST_NOT_ALLOWED | REINSPECTION_ITEM_LISTING_MISMATCH"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404", description = "PRODUCT_NOT_FOUND")
    })
    @PostMapping(
            path = "/api/v1/listings/{listingId}/reinspection-requests",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<ReinspectionRequestResponse>> request(
            @PathVariable Long listingId, @Valid @RequestBody ReinspectionRequestCreateRequest request);

    @Operation(
            operationId = "reinspection02",
            summary = "재검수 완료",
            description =
                    "판매자가 재검수 요청을 완료 처리합니다. 요청의 판매자 본인만 호출할 수 있고, REQUESTED 상태의"
                            + " 요청만 완료할 수 있습니다. 완료 시 알림 이벤트를 발행합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "완료 처리 완료",
                content = @Content(schema = @Schema(implementation = ReinspectionRequestResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403", description = "REINSPECTION_ACCESS_DENIED"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404", description = "REINSPECTION_REQUEST_NOT_FOUND"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409", description = "REINSPECTION_ALREADY_PROCESSED")
    })
    @PostMapping(path = "/api/v1/reinspection-requests/{requestKey}/complete", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<ReinspectionRequestResponse>> complete(@PathVariable String requestKey);
}
