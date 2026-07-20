package com.c203.limit.domain.payment.controller;

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

    @Operation(operationId = "pay01", summary = "결제 요청", description = "요청\n권한: BUYER(본인 주문)\nHeader: Idempotency-Key(string, required)\n검증: 주문 상태 PENDING, 주문 금액과 결제 요청 금액 일치, 동일 주문의 미종결 결제 시도 존재 여부(Idempotency-Key)\n처리: payments 생성(status=REQUESTED), pg_provider=TOSS 고정, PG 결제창 초기화에 필요한 정보 반환", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "결제 요청 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/PaymentReadyResponse"))),
        @ApiResponse(responseCode = "400", description = "INVALID_INPUT"),
        @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "ORDER_NOT_PAYABLE / DUPLICATE_REQUEST")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/payments", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> pay01(
            @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/CreatePaymentRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "pay02", summary = "결제 승인 콜백(Webhook)", description = "요청\n권한: PUBLIC(HMAC 서명 검증으로 대체)\nHeader: tosspayments-webhook-transmission-time, tosspayments-webhook-signature\nBody: 토스 Webhook 표준 페이로드(paymentKey, orderId, status, totalAmount 등)\n검증: HMAC-SHA256 서명 재계산 후 헤더값과 일치 확인, 요청 금액과 승인 금액 일치, paymentKey 기준 중복 처리 방지\n처리: payments.status → APPROVED, payment_key·approved_amount·approved_at 기록, orders.status → PAID, PAYMENT_COMPLETED 이벤트를 outbox_events에 적재\n※ 10초 이내 200 응답 필수(초과 시 PG가 재전송)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "결제 승인 콜백(Webhook) 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/PaymentApprovedResponse"))),
        @ApiResponse(responseCode = "401", description = "INVALID_SIGNATURE"),
        @ApiResponse(responseCode = "404", description = "PAYMENT_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "AMOUNT_MISMATCH / ALREADY_PROCESSED")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/payment-webhooks/toss", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> pay02(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/TossWebhookPayload"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "pay03", summary = "결제 실패 처리", description = "요청\n권한: BUYER(본인 결제) 또는 PUBLIC(PG 콜백, 서명 검증)\nPath: paymentId(long)\n검증: payments 상태가 REQUESTED인지 확인(이미 종결된 결제 재처리 방지)\n처리: payments.status → FAILED, failed_reason 기록. orders.status는 PENDING으로 그대로 유지(재시도는 새 payments row 생성으로 처리). 재고는 즉시 복구하지 않음 — 구매권(purchase_right) 발급 시 걸린 기존 TTL이 계속 유효하며, TTL 내 재시도 가능. TTL 만료 시에만 기존 구매권 만료 로직(Redis TTL, Scheduler)이 재고를 복구함", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "결제 실패 처리 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/PaymentFailedResponse"))),
        @ApiResponse(responseCode = "404", description = "PAYMENT_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "PAYMENT_ALREADY_FINALIZED")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/payments/{paymentId}/failures", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> pay03(
            @PathVariable("paymentId") Long paymentId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/FailPaymentRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
}
