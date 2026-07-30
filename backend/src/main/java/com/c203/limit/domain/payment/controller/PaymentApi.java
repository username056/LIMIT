package com.c203.limit.domain.payment.controller;

import com.c203.limit.domain.payment.dto.request.ConfirmPaymentRequest;
import com.c203.limit.domain.payment.dto.request.CreatePaymentRequest;
import com.c203.limit.domain.payment.dto.response.PaymentApiResponse;
import com.c203.limit.domain.payment.dto.response.PaymentResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "08. 결제", description = "매물 결제 요청 생성·조회 API")
public interface PaymentApi {

    @Operation(
            operationId = "payment01",
            summary = "결제 요청 생성",
            description = "판매중 매물을 예약하고 결제 요청을 생성합니다. 동일한 idempotencyKey로 재요청하면 "
                    + "기존 결제 요청을 그대로 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "결제 요청 생성 성공",
                content = @Content(schema = @Schema(implementation = PaymentApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "SELF_PURCHASE_NOT_ALLOWED"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "LISTING_NOT_FOUND"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "LISTING_NOT_ON_SALE / IDEMPOTENCY_KEY_CONFLICT / PAYMENT_REQUEST_CONFLICT")
    })
    @PostMapping(
            path = "/api/v1/payments",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @Valid @RequestBody CreatePaymentRequest request);

    @Operation(
            operationId = "payment03",
            summary = "결제 승인(confirm)",
            description = "Toss 결제창에서 승인된 결제를 서버에서 확정합니다. orderId·금액을 저장된 결제 "
                    + "요청과 대조한 뒤 Toss 승인 API를 호출하고, 성공하면 결제를 APPROVED로, 매물을 "
                    + "PAID로 전환합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "결제 승인 성공",
                content = @Content(schema = @Schema(implementation = PaymentApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "PAYMENT_ORDER_ID_MISMATCH / PAYMENT_AMOUNT_MISMATCH"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "PAYMENT_ACCESS_DENIED"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PAYMENT_NOT_FOUND"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "PAYMENT_NOT_CONFIRMABLE"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "PAYMENT_CONFIRM_REJECTED"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "PAYMENT_CONFIRM_RETRYABLE")
    })
    @PostMapping(
            path = "/api/v1/payments/{paymentId}/confirm",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<PaymentResponse>> confirmPayment(
            @PathVariable Long paymentId, @Valid @RequestBody ConfirmPaymentRequest request);

    @Operation(
            operationId = "payment02",
            summary = "결제 상세 조회",
            description = "본인이 요청한 결제 내역을 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "결제 상세 조회 성공",
                content = @Content(schema = @Schema(implementation = PaymentApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "PAYMENT_ACCESS_DENIED"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PAYMENT_NOT_FOUND")
    })
    @GetMapping(path = "/api/v1/payments/{paymentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiResponse<PaymentResponse>> getPayment(@PathVariable Long paymentId);
}
