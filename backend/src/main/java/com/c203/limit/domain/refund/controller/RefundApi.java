package com.c203.limit.domain.refund.controller;

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

    @Operation(operationId = "refund01", summary = "환불 요청", description = "요청\n권한: BUYER(본인 결제) 또는 OPERATOR+ 또는 내부 호출(ORDER-03)\nPath: paymentId(long)\nHeader: Idempotency-Key(string, required)\ntriggerType: ORDER_CANCEL(주문취소) | INSPECTION_FAILED(검증실패, 2차) | DISPUTE_RESOLVED(분쟁판정, 추후)\n검증: payments 상태 APPROVED, 대상 orders.status가 CONFIRMED가 아님(구매확정 이후에는 환불 자체를 차단하고 분쟁 절차로 유도, triggerType=DISPUTE_RESOLVED는 예외 허용), cancelAmount가 (승인금액 - 기존 환불 누계) 이하, Idempotency-Key 중복 확인\n처리: refunds 생성(status=REQUESTED), PG 환불 API 호출", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "환불 요청 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/RefundReadyResponse"))),
        @ApiResponse(responseCode = "400", description = "INVALID_INPUT"),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
        @ApiResponse(responseCode = "404", description = "PAYMENT_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "PAYMENT_NOT_REFUNDABLE / ORDER_ALREADY_CONFIRMED / REFUND_AMOUNT_EXCEEDED / DUPLICATE_REQUEST")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/payments/{paymentId}/refunds", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> refund01(
            @PathVariable("paymentId") Long paymentId,
            @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/CreateRefundRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "refund02", summary = "환불 처리 결과 반영", description = "요청\n권한: PUBLIC(PG 콜백, 서명 검증) 또는 내부 시스템 호출\nPath: refundId(long)\n검증: refunds 상태가 REQUESTED인지 확인\n처리: refunds.status → COMPLETED 또는 FAILED, completed_at 기록. COMPLETED이면 대상 orders.status → CANCELLED로 최종 전환(ORDER-03에서 CANCEL_REQUESTED로 걸어둔 주문 확정) 및 재고 복구 이벤트를 outbox_events에 적재. FAILED이면 orders는 CANCEL_REQUESTED 상태로 유지되어 재시도 또는 관리자 개입 대기, 재고는 복구하지 않음")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "환불 처리 결과 반영 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/RefundCompletedResponse"))),
        @ApiResponse(responseCode = "404", description = "REFUND_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "REFUND_ALREADY_FINALIZED")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/payments/refunds/{refundId}/completions", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> refund02(
            @PathVariable("refundId") Long refundId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/CompleteRefundRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "refund03", summary = "환불 내역 조회", description = "요청\n권한: BUYER(본인 결제) 또는 OPERATOR+\nPath: paymentId(long)\n처리: 재시도 이력 포함 전체 환불 시도 목록 반환(실패 후 재시도 시 새 row로 누적)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "환불 내역 조회 성공", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/RefundSummaryResponse")))),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
        @ApiResponse(responseCode = "404", description = "PAYMENT_NOT_FOUND")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/payments/{paymentId}/refunds", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> refund03(
            @PathVariable("paymentId") Long paymentId
    );
}
