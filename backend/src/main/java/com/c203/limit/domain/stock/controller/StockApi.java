package com.c203.limit.domain.stock.controller;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "10. 재고")
public interface StockApi {

    @Operation(operationId = "stock01", summary = "재고 예약", description = "요청\n권한: BUYER\nPath: saleId(Long, required)\nHeader: Idempotency-Key(String, required)\nBody: StockReservationCreateRequest\n{\"purchasePassToken\":\"signed-purchase-token\",\"quantity\":1}\n검증: ACTIVE 구매권, 토큰 소유자, 구매 제한, 가용 재고\n처리: Redis Lua Script로 구매권 확인 및 가용 재고 감소를 원자적으로 처리", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "재고 예약 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/StockReservationCreateResponse"))),
        @ApiResponse(responseCode = "404", description = "SALE_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "OUT_OF_STOCK / DUPLICATE_REQUEST"),
        @ApiResponse(responseCode = "422", description = "PURCHASE_PASS_INVALID")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/sales/{saleId}/stock-reservations", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> stock01(
            @PathVariable("saleId") Long saleId,
            @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/StockReservationCreateRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "stock02", summary = "재고 차감 확정", description = "요청\n권한: INTERNAL_PAYMENT_SERVICE\nPath: reservationId(Long, required)\nHeader: Idempotency-Key(String, required)\nBody: StockReservationConfirmRequest\n{\"orderId\":3001,\"paymentId\":4001,\"paidAt\":\"2026-07-16T10:05:00+09:00\"}\n검증: RESERVED 상태, 결제 완료, 주문·예약 일치\n처리: 트랜잭션으로 예약 수량을 판매 확정 수량으로 이동", security = @SecurityRequirement(name = "internalApiKey"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "재고 차감 확정 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/StockReservationConfirmResponse"))),
        @ApiResponse(responseCode = "404", description = "RESERVATION_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "RESERVATION_ALREADY_PROCESSED"),
        @ApiResponse(responseCode = "422", description = "RESERVATION_EXPIRED")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/internal/stock-reservations/{reservationId}/confirmations", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> stock02(
            @PathVariable("reservationId") Long reservationId,
            @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/StockReservationConfirmRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "stock03", summary = "재고 복구", description = "요청\n권한: INTERNAL_PAYMENT_SERVICE 또는 INTERNAL_ORDER_SERVICE\nPath: reservationId(Long, required)\nHeader: Idempotency-Key(String, required)\nBody: StockReservationReleaseRequest\n{\"reason\":\"PAYMENT_FAILED\",\"orderId\":3001,\"occurredAt\":\"2026-07-16T10:06:00+09:00\"}\n검증: 복구 가능한 예약 상태, 중복 복구 여부\n처리: 예약 수량을 가용 재고로 원자적 반환", security = @SecurityRequirement(name = "internalApiKey"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "재고 복구 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/StockReservationReleaseResponse"))),
        @ApiResponse(responseCode = "404", description = "RESERVATION_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "INVALID_RESERVATION_STATUS / IDEMPOTENCY_KEY_CONFLICT")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/internal/stock-reservations/{reservationId}/releases", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> stock03(
            @PathVariable("reservationId") Long reservationId,
            @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/StockReservationReleaseRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "stock04", summary = "드롭 품절 처리", description = "요청\n권한: INTERNAL_STOCK_SERVICE\nPath: saleId(Long, required)\nHeader: Idempotency-Key(String, required)\nBody: SaleSoldOutRequest\n{\"inventoryId\":501,\"expectedAvailableQuantity\":0,\"reason\":\"INVENTORY_DEPLETED\"}\n검증: 실제 availableQuantity=0, 허용된 판매 상태\n처리: OPEN 또는 TEMP_SOLD_OUT 상태를 SOLD_OUT으로 전환", security = @SecurityRequirement(name = "internalApiKey"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "드롭 품절 처리 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/SaleSoldOutResponse"))),
        @ApiResponse(responseCode = "404", description = "SALE_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "INVENTORY_NOT_DEPLETED / INVALID_SALE_STATUS")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/internal/sales/{saleId}/sold-out-events", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> stock04(
            @PathVariable("saleId") Long saleId,
            @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/SaleSoldOutRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
}
