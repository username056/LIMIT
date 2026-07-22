package com.c203.limit.domain.queue.controller;

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

    @Operation(operationId = "queue01", summary = "대기열 입장", description = "요청\n권한: BUYER\nPath: saleId(Long, required)\nBody: QueueJoinRequest\n{\"captchaVerificationToken\":\"captcha-verification-token\",\"deviceFingerprint\":\"hashed-device-fingerprint\"}\n검증: 선착순 판매 여부, 입장 가능 상태, 동일 회원 중복 참여, CAPTCHA 토큰 유효성\n처리: 서버 수신 순서 기준 sequenceNo 원자적 발급", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "대기열 입장 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/QueueJoinResponse"))),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @ApiResponse(responseCode = "404", description = "SALE_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "DUPLICATE_QUEUE_PARTICIPATION"),
        @ApiResponse(responseCode = "422", description = "SALE_NOT_QUEUE_OPEN / CAPTCHA_VERIFICATION_FAILED"),
        @ApiResponse(responseCode = "429", description = "TOO_MANY_REQUESTS")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/sales/{saleId}/queue-participations", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> queue01(
            @PathVariable("saleId") Long saleId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/QueueJoinRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "queue03", summary = "구매권 발급", description = "요청\n권한: INTERNAL_QUEUE_SERVICE\nPath: saleId(Long, required)\nHeader: Idempotency-Key(String, required)\nBody: PurchasePassIssueRequest\n{\"queueParticipationId\":1201,\"userId\":35,\"expiresInSeconds\":300}\n검증: ADMITTED 상태, 판매·회원 일치, 미발급 상태\n처리: 서명 토큰 생성, TTL 설정, 중복 발급 방지", security = @SecurityRequirement(name = "internalApiKey"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "구매권 발급 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/PurchasePassIssueResponse"))),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
        @ApiResponse(responseCode = "404", description = "QUEUE_PARTICIPATION_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "PURCHASE_PASS_ALREADY_ISSUED"),
        @ApiResponse(responseCode = "422", description = "QUEUE_NOT_ADMITTED")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/internal/sales/{saleId}/purchase-passes", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> queue03(
            @PathVariable("saleId") Long saleId,
            @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/PurchasePassIssueRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "queue04", summary = "만료 구매권 회수", description = "요청\n권한: INTERNAL_SCHEDULER\nBody: PurchasePassExpireRequest\n{\"expiredBefore\":\"2026-07-16T10:08:00+09:00\",\"batchSize\":100}\n검증: ACTIVE 상태이며 expiresAt이 기준 시각 이전\n처리: 구매권 EXPIRED 전환, 예약 재고 복구, 다음 대기자 입장 요청", security = @SecurityRequirement(name = "internalApiKey"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "만료 구매권 회수 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/PurchasePassExpireResponse"))),
        @ApiResponse(responseCode = "400", description = "INVALID_INPUT"),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/internal/purchase-pass-expirations", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> queue04(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/PurchasePassExpireRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "queue05", summary = "CAPTCHA 검증", description = "요청\n권한: BUYER\nBody: CaptchaVerifyRequest\n{\"captchaToken\":\"provider-captcha-token\",\"deviceFingerprint\":\"hashed-device-fingerprint\",\"action\":\"QUEUE_JOIN\"}\n검증: CAPTCHA 제공자 검증, 요청 제한, 기기 식별값 검사\n처리: 성공 시 단기 검증 토큰 발급", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "CAPTCHA 검증 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/CaptchaVerifyResponse"))),
        @ApiResponse(responseCode = "400", description = "INVALID_INPUT"),
        @ApiResponse(responseCode = "422", description = "CAPTCHA_VERIFICATION_FAILED"),
        @ApiResponse(responseCode = "429", description = "TOO_MANY_REQUESTS")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/security/captcha/verifications", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> queue05(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/CaptchaVerifyRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "queue02", summary = "내 대기열 순번 조회", description = "요청\n권한: BUYER(본인 참여 정보)\nPath: saleId(Long, required)\nBody: 없음\n검증: Access Token 사용자와 참여 사용자 일치\n처리: 현재 순번, 앞 대기 인원, 예상 입장 시각 계산", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "내 대기열 순번 조회 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/QueueStatusResponse"))),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @ApiResponse(responseCode = "403", description = "QUEUE_ACCESS_DENIED"),
        @ApiResponse(responseCode = "404", description = "QUEUE_PARTICIPATION_NOT_FOUND")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/sales/{saleId}/queue-participations/me", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> queue02(
            @PathVariable("saleId") Long saleId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(type = "object"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
}
