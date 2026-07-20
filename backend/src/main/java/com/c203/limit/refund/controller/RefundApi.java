package com.c203.limit.refund.controller;

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

@Tag(name = "09. 환불")
public interface RefundApi {

    @Operation(operationId = "refund01", summary = "환불 요청", description = "요청\n권한: BUYER(본인 결제) 또는 OPERATOR+ 또는 내부 호출(ORDER-03)\nPath: paymentId(long)\nHeader: Idempotency-Key(string, required)\nBody:\n{\"cancelAmount\":123000,\"reason\":\"단순 변심\",\"triggerType\":\"ORDER_CANCEL\"}\ntriggerType: ORDER_CANCEL(주문취소) | INSPECTION_FAILED(검증실패, 2차) | DISPUTE_RESOLVED(분쟁판정, 추후)\n검증: payments 상태 APPROVED, 대상 orders.status가 CONFIRMED가 아님(구매확정 이후에는 환불 자체를 차단하고 분쟁 절차로 유도, triggerType=DISPUTE_RESOLVED는 예외 허용), cancelAmount가 (승인금액 - 기존 환불 누계) 이하, Idempotency-Key 중복 확인\n처리: refunds 생성(status=REQUESTED), PG 환불 API 호출\n\n응답\n201 Created\n{\"data\":{\"refundId\":1,\"paymentId\":1,\"cancelAmount\":123000,\"status\":\"REQUESTED\",\"requestedAt\":\"2026-07-16T12:30:00+09:00\"}}\n오류: 400 INVALID_INPUT, 403 FORBIDDEN, 404 PAYMENT_NOT_FOUND, 409 PAYMENT_NOT_REFUNDABLE, 409 ORDER_ALREADY_CONFIRMED, 409 REFUND_AMOUNT_EXCEEDED, 409 DUPLICATE_REQUEST", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/RefundReadyResponse"))),
        @ApiResponse(responseCode = "400", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "409", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/payments/{paymentId}/refunds", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> refund01(
            @PathVariable("paymentId") Long paymentId,
            @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/CreateRefundRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "refund02", summary = "환불 처리 결과 반영", description = "요청\n권한: PUBLIC(PG 콜백, 서명 검증) 또는 내부 시스템 호출\nPath: refundId(long)\nBody:\n{\"result\":\"COMPLETED\"}\n검증: refunds 상태가 REQUESTED인지 확인\n처리: refunds.status → COMPLETED 또는 FAILED, completed_at 기록. COMPLETED이면 대상 orders.status → CANCELLED로 최종 전환(ORDER-03에서 CANCEL_REQUESTED로 걸어둔 주문 확정) 및 재고 복구 이벤트를 outbox_events에 적재. FAILED이면 orders는 CANCEL_REQUESTED 상태로 유지되어 재시도 또는 관리자 개입 대기, 재고는 복구하지 않음\n\n응답\n200 OK\n{\"data\":{\"refundId\":1,\"status\":\"COMPLETED\",\"completedAt\":\"2026-07-16T12:35:00+09:00\",\"orderStatus\":\"CANCELLED\"}}\n오류: 404 REFUND_NOT_FOUND, 409 REFUND_ALREADY_FINALIZED")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/RefundCompletedResponse"))),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "409", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/payments/refunds/{refundId}/completions", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> refund02(
            @PathVariable("refundId") Long refundId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/CompleteRefundRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "refund03", summary = "환불 내역 조회", description = "요청\n권한: BUYER(본인 결제) 또는 OPERATOR+\nPath: paymentId(long)\n처리: 재시도 이력 포함 전체 환불 시도 목록 반환(실패 후 재시도 시 새 row로 누적)\n\n응답\n200 OK\n{\"data\":[{\"refundId\":1,\"cancelAmount\":123000,\"status\":\"FAILED\",\"requestedAt\":\"2026-07-16T12:30:00+09:00\",\"completedAt\":null},{\"refundId\":2,\"cancelAmount\":123000,\"status\":\"COMPLETED\",\"requestedAt\":\"2026-07-16T12:40:00+09:00\",\"completedAt\":\"2026-07-16T12:41:00+09:00\"}]}\n오류: 403 FORBIDDEN, 404 PAYMENT_NOT_FOUND", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/RefundSummaryResponse")))),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/payments/{paymentId}/refunds", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> refund03(
            @PathVariable("paymentId") Long paymentId
    );
}
