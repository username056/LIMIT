package com.c203.limit.domain.order.controller;

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

@Tag(name = "07. 주문")
public interface OrderApi {

    @Operation(operationId = "order01", summary = "주문 생성", description = "요청\n권한: BUYER\nHeader: Idempotency-Key(string, required)\n검증: 구매권 소유자·미사용·미만료 확인, 상품 판매 상태 확인, Idempotency-Key 중복 확인\n처리: orders 생성(status=PENDING), order_number 발급, 재고 예약 상태와 연동", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "주문 생성 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/OrderSummaryResponse"))),
        @ApiResponse(responseCode = "400", description = "INVALID_INPUT"),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @ApiResponse(responseCode = "404", description = "PURCHASE_RIGHT_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "PURCHASE_RIGHT_ALREADY_USED / DUPLICATE_REQUEST")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/orders", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> order01(
            @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/CreateOrderRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "order03", summary = "주문 취소", description = "요청\n권한: BUYER(본인 주문) 또는 OPERATOR+\nPath: orderId(long)\nreasonType: BUYER_SIMPLE_CHANGE(단순변심) | SELLER_FAULT(판매자 귀책) | NOT_DELIVERED(미배송)\n검증: State Machine으로 취소 가능 상태 확인\n처리:\n- orders.status=PENDING(결제 전)이면 → 즉시 CANCELLED 전환, 재고 복구 이벤트 발행\n- orders.status=PAID(결제 후)이면 → CANCEL_REQUESTED로 전환, reasonType 기준 환불액 서버 계산 후 REFUND-01 내부 호출. 환불 완료 이벤트 수신 시점에 최종 CANCELLED 전환(환불 실패 시 CANCELLED로 확정하지 않음)\n※ 환불액은 클라이언트가 지정하지 않고 reasonType에 따라 서버가 정책적으로 계산함(단순변심은 배송비 차감, 판매자귀책·미배송은 전액)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "주문 취소 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/OrderDetailResponse"))),
        @ApiResponse(responseCode = "400", description = "ORDER_NOT_CANCELLABLE"),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
        @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "INVALID_STATUS_TRANSITION")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/orders/{orderId}/cancellations", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> order03(
            @PathVariable("orderId") Long orderId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/CancelOrderRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "order04", summary = "구매 확정", description = "요청\n권한: BUYER(본인 주문)\nPath: orderId(long)\n검증: 주문 상태 PAID(배송 완료 등 선행 조건 포함), 이미 CONFIRMED·CANCELLED 아님(중복 확정·상태 역행 방지)\n처리: orders.status → CONFIRMED, confirmed_at 기록, 정산 프로세스 트리거 이벤트 발행", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "구매 확정 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/OrderDetailResponse"))),
        @ApiResponse(responseCode = "400", description = "INVALID_STATUS_TRANSITION"),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
        @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "ALREADY_CONFIRMED")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/orders/{orderId}/confirmations", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> order04(
            @PathVariable("orderId") Long orderId
    );
    @Operation(operationId = "order02", summary = "주문 목록 조회", description = "요청\n권한: MEMBER\nQuery: page, size, status(PENDING|PAID|CANCELLED|CONFIRMED), role(BUYER|SELLER)\n처리: role=BUYER면 buyer_id=본인 기준, role=SELLER면 seller_id=본인 기준으로 스코프 제한", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "주문 목록 조회 성공", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/OrderSummaryResponse")))),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/orders", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> order02(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "role", required = false) String role
    );
    @Operation(operationId = "order021", summary = "주문 상세 조회", description = "요청\n권한: MEMBER\nPath: orderId(long)\n검증: 본인이 구매자 또는 판매자인 주문만 조회 가능", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "주문 상세 조회 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/OrderDetailResponse"))),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
        @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/orders/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> order021(
            @PathVariable("orderId") Long orderId
    );
}
