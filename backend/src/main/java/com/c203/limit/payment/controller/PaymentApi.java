package com.c203.limit.payment.controller;

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

@Tag(name = "08. 결제")
public interface PaymentApi {

    @Operation(operationId = "pay01", summary = "결제 요청", description = "요청\n권한: BUYER(본인 주문)\nHeader: Idempotency-Key(string, required)\nBody:\n{\"orderId\":1,\"method\":\"CARD\"}\n검증: 주문 상태 PENDING, 주문 금액과 결제 요청 금액 일치, 동일 주문의 미종결 결제 시도 존재 여부(Idempotency-Key)\n처리: payments 생성(status=REQUESTED), pg_provider=TOSS 고정, PG 결제창 초기화에 필요한 정보 반환\n\n응답\n201 Created\n{\"data\":{\"paymentId\":1,\"orderId\":1,\"pgProvider\":\"TOSS\",\"requestedAmount\":123000,\"status\":\"REQUESTED\",\"clientKey\":\"test_ck_...\",\"orderNumber\":\"ORD-20260716-0001\"}}\n오류: 400 INVALID_INPUT, 404 ORDER_NOT_FOUND, 409 ORDER_NOT_PAYABLE, 409 DUPLICATE_REQUEST", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/PaymentReadyResponse"))),
        @ApiResponse(responseCode = "400", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "409", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/payments", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> pay01(
            @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/CreatePaymentRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "pay02", summary = "결제 승인 콜백(Webhook)", description = "요청\n권한: PUBLIC(HMAC 서명 검증으로 대체)\nHeader: tosspayments-webhook-transmission-time, tosspayments-webhook-signature\nBody: 토스 Webhook 표준 페이로드(paymentKey, orderId, status, totalAmount 등)\n검증: HMAC-SHA256 서명 재계산 후 헤더값과 일치 확인, 요청 금액과 승인 금액 일치, paymentKey 기준 중복 처리 방지\n처리: payments.status → APPROVED, payment_key·approved_amount·approved_at 기록, orders.status → PAID, PAYMENT_COMPLETED 이벤트를 outbox_events에 적재\n※ 10초 이내 200 응답 필수(초과 시 PG가 재전송)\n\n응답\n200 OK\n{\"data\":{\"paymentId\":1,\"orderId\":1,\"status\":\"APPROVED\",\"approvedAmount\":123000,\"approvedAt\":\"2026-07-16T12:10:00+09:00\"}}\n오류: 401 INVALID_SIGNATURE, 404 PAYMENT_NOT_FOUND, 409 AMOUNT_MISMATCH, 409 ALREADY_PROCESSED")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/PaymentApprovedResponse"))),
        @ApiResponse(responseCode = "401", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "409", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/payment-webhooks/toss", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> pay02(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/TossWebhookPayload"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "pay03", summary = "결제 실패 처리", description = "요청\n권한: BUYER(본인 결제) 또는 PUBLIC(PG 콜백, 서명 검증)\nPath: paymentId(long)\nBody:\n{\"failureCode\":\"USER_CANCEL\",\"failureMessage\":\"사용자가 결제를 취소했습니다.\"}\n검증: payments 상태가 REQUESTED인지 확인(이미 종결된 결제 재처리 방지)\n처리: payments.status → FAILED, failed_reason 기록. orders.status는 PENDING으로 그대로 유지(재시도는 새 payments row 생성으로 처리). 재고는 즉시 복구하지 않음 — 구매권(purchase_right) 발급 시 걸린 기존 TTL이 계속 유효하며, TTL 내 재시도 가능. TTL 만료 시에만 기존 구매권 만료 로직(Redis TTL, Scheduler)이 재고를 복구함\n\n응답\n200 OK\n{\"data\":{\"paymentId\":1,\"status\":\"FAILED\",\"failedReason\":\"사용자가 결제를 취소했습니다.\",\"orderStatus\":\"PENDING\",\"purchaseRightExpiresAt\":\"2026-07-16T12:15:00+09:00\",\"updatedAt\":\"2026-07-16T12:05:00+09:00\"}}\n오류: 404 PAYMENT_NOT_FOUND, 409 PAYMENT_ALREADY_FINALIZED", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/PaymentFailedResponse"))),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "409", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/payments/{paymentId}/failures", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> pay03(
            @PathVariable("paymentId") Long paymentId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/FailPaymentRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
}
