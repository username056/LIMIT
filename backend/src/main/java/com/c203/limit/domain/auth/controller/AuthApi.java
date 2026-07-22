package com.c203.limit.domain.auth.controller;

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

@Tag(name = "01. 인증")
public interface AuthApi {

    @Operation(operationId = "auth03", summary = "이메일 회원가입", description = "요청\n권한: PUBLIC\n검증: 이메일·닉네임 중복, 비밀번호 정책, 필수 약관 동의\n처리: members 생성, BUYER 역할 부여, 기본 알림 설정·장바구니 생성")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "이메일 회원가입 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/SignupResponse"))),
        @ApiResponse(responseCode = "400", description = "INVALID_INPUT"),
        @ApiResponse(responseCode = "409", description = "EMAIL_DUPLICATED / NICKNAME_DUPLICATED")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/members", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth03(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/SignupRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "auth04", summary = "이메일 로그인", description = "요청\n권한: PUBLIC\n처리: 자격 증명·회원 상태·활성 제재 확인, last_login_at 갱신")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "이메일 로그인 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/LoginResponse"))),
        @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS"),
        @ApiResponse(responseCode = "403", description = "MEMBER_WITHDRAWN / LOGIN_RESTRICTED"),
        @ApiResponse(responseCode = "429", description = "LOGIN_ATTEMPT_LIMITED")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/auth/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth04(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/LoginRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "auth05", summary = "인증 토큰 재발급", description = "요청\n권한: REFRESH_TOKEN\n처리: 만료·폐기 여부 검증, Refresh Token Rotation 적용 가능", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "인증 토큰 재발급 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/TokenResponse"))),
        @ApiResponse(responseCode = "401", description = "INVALID_REFRESH_TOKEN / EXPIRED_REFRESH_TOKEN / REVOKED_REFRESH_TOKEN")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/auth/token-refreshes", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth05(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/TokenRefreshRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "auth06", summary = "로그아웃", description = "요청\n권한: MEMBER\nHeader: Authorization: Bearer {accessToken}\n처리: Refresh Token 폐기, Access Token 잔여 시간 블랙리스트 등록", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "로그아웃 성공"),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/auth/session-revocations", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth06(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/LogoutRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "auth07", summary = "소셜 로그인", description = "요청\n권한: PUBLIC\nPath: provider(GOOGLE|KAKAO|NAVER)\n처리: social_accounts 조회·생성, 최초 로그인 시 members 및 BUYER 역할 생성")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "소셜 로그인 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/SocialLoginResponse"))),
        @ApiResponse(responseCode = "400", description = "UNSUPPORTED_PROVIDER"),
        @ApiResponse(responseCode = "401", description = "SOCIAL_AUTH_FAILED"),
        @ApiResponse(responseCode = "409", description = "SOCIAL_ACCOUNT_CONFLICT")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/auth/social-sessions/{provider}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth07(
            @PathVariable("provider") String provider,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/SocialLoginRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "auth01", summary = "이메일 중복 확인", description = "요청\n권한: PUBLIC\nQuery: email(string, required)\n검증: 이메일 형식")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "이메일 중복 확인 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/EmailAvailabilityResponse"))),
        @ApiResponse(responseCode = "400", description = "INVALID_EMAIL_FORMAT")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/auth/email-availability", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth01(
            @RequestParam(name = "email", required = false) String email
    );
    @Operation(operationId = "auth02", summary = "닉네임 중복 확인", description = "요청\n권한: PUBLIC\nQuery: nickname(string, required, 2~20자)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "닉네임 중복 확인 성공", content = @Content(schema = @Schema(ref = "#/components/schemas/NicknameAvailabilityResponse"))),
        @ApiResponse(responseCode = "400", description = "INVALID_NICKNAME_FORMAT")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/auth/nickname-availability", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth02(
            @RequestParam(name = "nickname", required = false) String nickname
    );
    @Operation(operationId = "auth08", summary = "소셜 연동 계정 목록 조회", description = "요청\n권한: MEMBER\nHeader: Authorization: Bearer {accessToken}", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "소셜 연동 계정 목록 조회 성공", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/SocialAccountResponse")))),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/members/me/social-accounts", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth08();
    @Operation(operationId = "auth09", summary = "소셜 연동 해제", description = "요청\n권한: MEMBER\nPath: socialAccountId(long)\n검증: 본인 소유 연동 계정, 해제 후 로그인 수단이 최소 1개 이상 남아야 함", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "소셜 연동 해제 성공"),
        @ApiResponse(responseCode = "400", description = "LAST_LOGIN_METHOD"),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
        @ApiResponse(responseCode = "404", description = "SOCIAL_ACCOUNT_NOT_FOUND")
    })
    @RequestMapping(method = RequestMethod.DELETE, path = "/api/v1/members/me/social-accounts/{socialAccountId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth09(
            @PathVariable("socialAccountId") Long socialAccountId
    );
}
