package com.c203.limit.queue.controller;

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

@Tag(name = "11. 대기열")
public interface QueueApi {

    @Operation(operationId = "queue01", summary = "대기열 입장", description = "요청\n권한: BUYER\nPath: saleId(Long, required)\nBody: QueueJoinRequest\n{\"captchaVerificationToken\":\"captcha-verification-token\",\"deviceFingerprint\":\"hashed-device-fingerprint\"}\n검증: 선착순 판매 여부, 입장 가능 상태, 동일 회원 중복 참여, CAPTCHA 토큰 유효성\n처리: 서버 수신 순서 기준 sequenceNo 원자적 발급\n\n응답\n201 Created\nDTO: QueueJoinResponse\n{\"queueParticipationId\":1201,\"saleId\":10,\"sequenceNo\":532,\"status\":\"WAITING\",\"joinedAt\":\"2026-07-16T10:00:00+09:00\"}\n중복 요청: 기존 참여 정보와 함께 200 OK\n오류: 401 UNAUTHORIZED, 404 SALE_NOT_FOUND, 409 DUPLICATE_QUEUE_PARTICIPATION, 422 SALE_NOT_QUEUE_OPEN, 422 CAPTCHA_VERIFICATION_FAILED, 429 TOO_MANY_REQUESTS", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/QueueJoinResponse"))),
        @ApiResponse(responseCode = "401", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "409", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "422", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/sales/{saleId}/queue-participations", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> queue01(
            @PathVariable("saleId") Long saleId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/QueueJoinRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "queue02", summary = "내 대기열 순번 조회", description = "요청\n권한: BUYER(본인 참여 정보)\nPath: saleId(Long, required)\nBody: 없음\n검증: Access Token 사용자와 참여 사용자 일치\n처리: 현재 순번, 앞 대기 인원, 예상 입장 시각 계산\n\n응답\n200 OK\nDTO: QueueStatusResponse\n{\"queueParticipationId\":1201,\"saleId\":10,\"sequenceNo\":532,\"status\":\"WAITING\",\"aheadCount\":27,\"estimatedAdmissionAt\":\"2026-07-16T10:03:00+09:00\",\"purchasePass\":null,\"checkedAt\":\"2026-07-16T10:01:00+09:00\"}\n오류: 401 UNAUTHORIZED, 403 QUEUE_ACCESS_DENIED, 404 QUEUE_PARTICIPATION_NOT_FOUND", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/QueueStatusResponse"))),
        @ApiResponse(responseCode = "401", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/sales/{saleId}/queue-participations/me", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> queue02(
            @PathVariable("saleId") Long saleId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(type = "object"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "queue03", summary = "구매권 발급", description = "요청\n권한: INTERNAL_QUEUE_SERVICE\nPath: saleId(Long, required)\nHeader: Idempotency-Key(String, required)\nBody: PurchasePassIssueRequest\n{\"queueParticipationId\":1201,\"userId\":35,\"expiresInSeconds\":300}\n검증: ADMITTED 상태, 판매·회원 일치, 미발급 상태\n처리: 서명 토큰 생성, TTL 설정, 중복 발급 방지\n\n응답\n201 Created\nDTO: PurchasePassIssueResponse\n{\"purchasePassId\":801,\"saleId\":10,\"userId\":35,\"status\":\"ACTIVE\",\"purchaseToken\":\"signed-purchase-token\",\"issuedAt\":\"2026-07-16T10:03:00+09:00\",\"expiresAt\":\"2026-07-16T10:08:00+09:00\"}\n중복 요청: 기존 구매권과 함께 200 OK\n오류: 403 FORBIDDEN, 404 QUEUE_PARTICIPATION_NOT_FOUND, 409 PURCHASE_PASS_ALREADY_ISSUED, 422 QUEUE_NOT_ADMITTED", security = @SecurityRequirement(name = "internalApiKey"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/PurchasePassIssueResponse"))),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "409", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "422", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/internal/sales/{saleId}/purchase-passes", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> queue03(
            @PathVariable("saleId") Long saleId,
            @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/PurchasePassIssueRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "queue04", summary = "만료 구매권 회수", description = "요청\n권한: INTERNAL_SCHEDULER\nBody: PurchasePassExpireRequest\n{\"expiredBefore\":\"2026-07-16T10:08:00+09:00\",\"batchSize\":100}\n검증: ACTIVE 상태이며 expiresAt이 기준 시각 이전\n처리: 구매권 EXPIRED 전환, 예약 재고 복구, 다음 대기자 입장 요청\n\n응답\n200 OK\nDTO: PurchasePassExpireResponse\n{\"expiredCount\":37,\"releasedReservationCount\":12,\"nextAdmissionRequestedCount\":37,\"processedAt\":\"2026-07-16T10:08:01+09:00\"}\n오류: 400 INVALID_INPUT, 403 FORBIDDEN", security = @SecurityRequirement(name = "internalApiKey"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/PurchasePassExpireResponse"))),
        @ApiResponse(responseCode = "400", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/internal/purchase-pass-expirations", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> queue04(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/PurchasePassExpireRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "queue05", summary = "CAPTCHA 검증", description = "요청\n권한: BUYER\nBody: CaptchaVerifyRequest\n{\"captchaToken\":\"provider-captcha-token\",\"deviceFingerprint\":\"hashed-device-fingerprint\",\"action\":\"QUEUE_JOIN\"}\n검증: CAPTCHA 제공자 검증, 요청 제한, 기기 식별값 검사\n처리: 성공 시 단기 검증 토큰 발급\n\n응답\n200 OK\nDTO: CaptchaVerifyResponse\n{\"isVerified\":true,\"verificationToken\":\"short-lived-verification-token\",\"expiresAt\":\"2026-07-16T10:02:00+09:00\"}\n오류: 400 INVALID_INPUT, 422 CAPTCHA_VERIFICATION_FAILED, 429 TOO_MANY_REQUESTS", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/CaptchaVerifyResponse"))),
        @ApiResponse(responseCode = "400", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "422", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "429", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/security/captcha/verifications", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> queue05(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/CaptchaVerifyRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
}
