package com.c203.limit.domain.payment.controller;

import com.c203.limit.domain.payment.dto.response.OrderSummaryResponse;
import com.c203.limit.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

@Tag(name = "08. 결제", description = "매물 결제 요청·재시도·주문 내역 API")
public interface OrderApi {

    @Operation(
            operationId = "order01",
            summary = "내 주문 내역 목록 조회",
            description = "본인이 시도한 결제를 최신순으로 조회한다. 결제창 진입 전(REQUESTED) 시도는 "
                    + "아직 주문이 아니므로 목록에서 제외한다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "주문 내역 목록 조회 성공",
                content = @Content(
                        array = @io.swagger.v3.oas.annotations.media.ArraySchema(
                                schema = @Schema(implementation = OrderSummaryResponse.class)))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
    })
    @GetMapping(path = "/api/v1/orders", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<List<OrderSummaryResponse>>> listOrders();
}
