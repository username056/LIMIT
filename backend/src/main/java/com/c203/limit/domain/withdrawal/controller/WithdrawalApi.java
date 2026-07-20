package com.c203.limit.domain.withdrawal.controller;

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

@Tag(name = "15. 출금")
public interface WithdrawalApi {

    @Operation(operationId = "withdrawal01", summary = "회원 탈퇴 요청", description = "요청\n권한: MEMBER\n검증: LOCAL 회원은 비밀번호 재확인, 진행 중 주문·분쟁·환불·정산 여부\n처리: 요청 생성 후 members.status를 WITHDRAWAL_PENDING으로 변경하거나 제한 사유 기록", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "회원 탈퇴 요청 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/WithdrawalRequestResponse"))),
        @ApiResponse(responseCode = "409", description = "WITHDRAWAL_BLOCKED")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/members/me/withdrawal-requests", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> withdrawal01(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/CreateWithdrawalRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "withdrawal02", summary = "최근 회원 탈퇴 요청 조회", description = "요청\n권한: MEMBER", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "최근 회원 탈퇴 요청 조회 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/WithdrawalRequestResponse"))),
        @ApiResponse(responseCode = "404", description = "WITHDRAWAL_REQUEST_NOT_FOUND")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/members/me/withdrawal-requests/latest", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> withdrawal02();
    @Operation(operationId = "withdrawal03", summary = "회원 탈퇴 요청 취소", description = "요청\n권한: MEMBER\nPath: withdrawalRequestId(long)\n검증: 본인의 REQUESTED 상태 요청만 취소 가능\n처리: 요청 상태 CANCELED, members.status ACTIVE 복구", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "회원 탈퇴 요청 취소 성공"),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
        @ApiResponse(responseCode = "404", description = "WITHDRAWAL_REQUEST_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "WITHDRAWAL_REQUEST_NOT_CANCELABLE")
    })
    @RequestMapping(method = RequestMethod.DELETE, path = "/api/v1/members/me/withdrawal-requests/{withdrawalRequestId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> withdrawal03(
            @PathVariable("withdrawalRequestId") Long withdrawalRequestId
    );
}
