package com.c203.limit.auth.controller;

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

    @Operation(operationId = "auth01", summary = "이메일 중복 확인", description = "요청\n권한: PUBLIC\nQuery: email(string, required)\n검증: 이메일 형식\n\n응답\n200 OK\n{\"data\":{\"email\":\"user@example.com\",\"available\":true}}\n오류: 400 INVALID_EMAIL_FORMAT")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/EmailAvailabilityResponse"))),
        @ApiResponse(responseCode = "400", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/auth/email-availability", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth01(
            @RequestParam(name = "email", required = false) String email
    );
    @Operation(operationId = "auth02", summary = "닉네임 중복 확인", description = "요청\n권한: PUBLIC\nQuery: nickname(string, required, 2~20자)\n\n응답\n200 OK\n{\"data\":{\"nickname\":\"openrunner\",\"available\":true}}\n오류: 400 INVALID_NICKNAME_FORMAT")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/NicknameAvailabilityResponse"))),
        @ApiResponse(responseCode = "400", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/auth/nickname-availability", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth02(
            @RequestParam(name = "nickname", required = false) String nickname
    );
    @Operation(operationId = "auth03", summary = "이메일 회원가입", description = "요청\n권한: PUBLIC\nBody:\n{\"email\":\"user@example.com\",\"password\":\"Password123!\",\"nickname\":\"openrunner\",\"phone\":\"01012345678\"}\n검증: 이메일·닉네임 중복, 비밀번호 정책, 필수 약관 동의\n처리: members 생성, BUYER 역할 부여, 기본 알림 설정·장바구니 생성\n\n응답\n201 Created\n{\"data\":{\"memberId\":1,\"email\":\"user@example.com\",\"nickname\":\"openrunner\",\"status\":\"ACTIVE\",\"roles\":[\"BUYER\"],\"createdAt\":\"2026-07-16T11:00:00+09:00\"}}\n오류: 400 INVALID_INPUT, 409 EMAIL_DUPLICATED, 409 NICKNAME_DUPLICATED")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/SignupResponse"))),
        @ApiResponse(responseCode = "400", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "409", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/members", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth03(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/SignupRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "auth04", summary = "이메일 로그인", description = "요청\n권한: PUBLIC\nBody:\n{\"email\":\"user@example.com\",\"password\":\"Password123!\"}\n처리: 자격 증명·회원 상태·활성 제재 확인, last_login_at 갱신\n\n응답\n200 OK\n{\"data\":{\"accessToken\":\"...\",\"refreshToken\":\"...\",\"tokenType\":\"Bearer\",\"expiresIn\":1800,\"member\":{\"memberId\":1,\"nickname\":\"openrunner\",\"roles\":[\"BUYER\"]}}}\n오류: 401 INVALID_CREDENTIALS, 403 MEMBER_WITHDRAWN, 403 LOGIN_RESTRICTED, 429 LOGIN_ATTEMPT_LIMITED")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/LoginResponse"))),
        @ApiResponse(responseCode = "401", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "429", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/auth/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth04(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/LoginRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "auth05", summary = "인증 토큰 재발급", description = "요청\n권한: REFRESH_TOKEN\nBody:\n{\"refreshToken\":\"...\"}\n처리: 만료·폐기 여부 검증, Refresh Token Rotation 적용 가능\n\n응답\n200 OK\n{\"data\":{\"accessToken\":\"...\",\"refreshToken\":\"...\",\"tokenType\":\"Bearer\",\"expiresIn\":1800}}\n오류: 401 INVALID_REFRESH_TOKEN, 401 EXPIRED_REFRESH_TOKEN, 401 REVOKED_REFRESH_TOKEN", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/TokenResponse"))),
        @ApiResponse(responseCode = "401", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/auth/token-refreshes", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth05(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/TokenRefreshRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "auth06", summary = "로그아웃", description = "요청\n권한: MEMBER\nHeader: Authorization: Bearer {accessToken}\nBody:\n{\"refreshToken\":\"...\"}\n처리: Refresh Token 폐기, Access Token 잔여 시간 블랙리스트 등록\n\n응답\n204 No Content\n오류: 401 UNAUTHORIZED", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "명세 응답"),
        @ApiResponse(responseCode = "401", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/auth/session-revocations", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth06(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/LogoutRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "auth07", summary = "소셜 로그인", description = "요청\n권한: PUBLIC\nPath: provider(GOOGLE|KAKAO|NAVER)\nBody:\n{\"authorizationCode\":\"...\",\"redirectUri\":\"https://.../oauth/callback\"}\n처리: social_accounts 조회·생성, 최초 로그인 시 members 및 BUYER 역할 생성\n\n응답\n200 OK\n{\"data\":{\"isNewMember\":false,\"accessToken\":\"...\",\"refreshToken\":\"...\",\"member\":{\"memberId\":1,\"email\":\"user@example.com\",\"nickname\":\"openrunner\",\"roles\":[\"BUYER\"]}}}\n오류: 400 UNSUPPORTED_PROVIDER, 401 SOCIAL_AUTH_FAILED, 409 SOCIAL_ACCOUNT_CONFLICT")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/SocialLoginResponse"))),
        @ApiResponse(responseCode = "400", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "401", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "409", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/auth/social-sessions/{provider}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth07(
            @PathVariable("provider") String provider,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/SocialLoginRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "auth08", summary = "소셜 연동 계정 목록 조회", description = "요청\n권한: MEMBER\nHeader: Authorization: Bearer {accessToken}\n\n응답\n200 OK\n{\"data\":[{\"socialAccountId\":11,\"provider\":\"GOOGLE\",\"providerEmail\":\"u***@gmail.com\",\"connectedAt\":\"2026-07-01T10:00:00+09:00\"}]}\n오류: 401 UNAUTHORIZED", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(array = @ArraySchema(schema = @Schema(ref = "#/components/schemas/SocialAccountResponse")))),
        @ApiResponse(responseCode = "401", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/members/me/social-accounts", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth08();
    @Operation(operationId = "auth09", summary = "소셜 연동 해제", description = "요청\n권한: MEMBER\nPath: socialAccountId(long)\n검증: 본인 소유 연동 계정, 해제 후 로그인 수단이 최소 1개 이상 남아야 함\n\n응답\n204 No Content\n오류: 400 LAST_LOGIN_METHOD, 403 FORBIDDEN, 404 SOCIAL_ACCOUNT_NOT_FOUND", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "명세 응답"),
        @ApiResponse(responseCode = "400", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "403", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "404", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.DELETE, path = "/api/v1/members/me/social-accounts/{socialAccountId}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth09(
            @PathVariable("socialAccountId") Long socialAccountId
    );
}
