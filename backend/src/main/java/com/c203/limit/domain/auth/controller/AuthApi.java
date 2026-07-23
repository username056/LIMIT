package com.c203.limit.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "01. 인증")
public interface AuthApi {
    @Operation(operationId = "auth03", summary = "이메일 회원가입")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "회원가입 성공"),
        @ApiResponse(responseCode = "400", description = "INVALID_INPUT / AUTH016"),
        @ApiResponse(responseCode = "409", description = "EMAIL_DUPLICATED / NICKNAME_DUPLICATED")
    })
    @RequestMapping(
            method = RequestMethod.POST,
            path = "/api/v1/members",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth03(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            ref =
                                                                    "#/components/schemas/SignupRequest")))
                    @org.springframework.web.bind.annotation.RequestBody
                    Object body);

    @Operation(operationId = "auth04", summary = "이메일 로그인")
    @RequestMapping(
            method = RequestMethod.POST,
            path = "/api/v1/auth/sessions",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth04(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            ref =
                                                                    "#/components/schemas/LoginRequest")))
                    @org.springframework.web.bind.annotation.RequestBody
                    Object body);

    @Operation(operationId = "auth05", summary = "HttpOnly 쿠키로 Access Token 재발급")
    @RequestMapping(
            method = RequestMethod.POST,
            path = "/api/v1/auth/token-refreshes",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth05();

    @Operation(operationId = "auth06", summary = "로그아웃 및 Refresh Token 폐기")
    @RequestMapping(
            method = RequestMethod.POST,
            path = "/api/v1/auth/session-revocations",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth06();

    @Operation(operationId = "auth07", summary = "소셜 로그인 콜백 완료")
    @RequestMapping(
            method = RequestMethod.POST,
            path = "/api/v1/auth/social-sessions/{provider}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth07(
            @PathVariable("provider") String provider,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            ref =
                                                                    "#/components/schemas/SocialLoginRequest")))
                    @org.springframework.web.bind.annotation.RequestBody
                    Object body);

    @Operation(operationId = "auth01", summary = "이메일 중복 확인")
    @RequestMapping(
            method = RequestMethod.GET,
            path = "/api/v1/auth/email-availability",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth01(@RequestParam("email") String email);

    @Operation(operationId = "auth02", summary = "닉네임 중복 확인")
    @RequestMapping(
            method = RequestMethod.GET,
            path = "/api/v1/auth/nickname-availability",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth02(@RequestParam("nickname") String nickname);

    @Operation(
            operationId = "auth08",
            summary = "연결된 소셜 계정 조회",
            security = @SecurityRequirement(name = "bearerAuth"))
    @RequestMapping(
            method = RequestMethod.GET,
            path = "/api/v1/members/me/social-accounts",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth08();

    @Operation(
            operationId = "auth09",
            summary = "소셜 계정 연결 해제",
            security = @SecurityRequirement(name = "bearerAuth"))
    @RequestMapping(
            method = RequestMethod.DELETE,
            path = "/api/v1/members/me/social-accounts/{socialAccountId}")
    ResponseEntity<Void> auth09(@PathVariable("socialAccountId") Long socialAccountId);

    @Operation(operationId = "auth10", summary = "이메일 인증 메일 발송")
    @RequestMapping(method = RequestMethod.POST, path = "/api/v1/auth/email-verification-requests")
    ResponseEntity<Void> auth10(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            ref =
                                                                    "#/components/schemas/EmailVerificationRequest")))
                    @org.springframework.web.bind.annotation.RequestBody
                    Object body);

    @Operation(operationId = "auth11", summary = "이메일 인증 완료")
    @RequestMapping(
            method = RequestMethod.POST,
            path = "/api/v1/auth/email-verifications",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth11(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            ref =
                                                                    "#/components/schemas/VerifyEmailRequest")))
                    @org.springframework.web.bind.annotation.RequestBody
                    Object body);

    @Operation(operationId = "auth12", summary = "소셜 로그인 인가 시작")
    @RequestMapping(
            method = RequestMethod.POST,
            path = "/api/v1/auth/social-authorizations/{provider}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth12(
            @PathVariable("provider") String provider,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            ref =
                                                                    "#/components/schemas/OAuthAuthorizationRequest")))
                    @org.springframework.web.bind.annotation.RequestBody
                    Object body);

    @Operation(operationId = "auth13", summary = "최초 소셜 회원가입 완료")
    @ApiResponse(
            responseCode = "200",
            content = @Content(schema = @Schema(ref = "#/components/schemas/SocialLoginResponse")))
    @RequestMapping(
            method = RequestMethod.POST,
            path = "/api/v1/auth/social-signups",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth13(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            ref =
                                                                    "#/components/schemas/CompleteSocialSignupRequest")))
                    @org.springframework.web.bind.annotation.RequestBody
                    Object body);

    @Operation(
            operationId = "auth14",
            summary = "소셜 계정 연동 인가 시작",
            security = @SecurityRequirement(name = "bearerAuth"))
    @RequestMapping(
            method = RequestMethod.POST,
            path = "/api/v1/members/me/social-authorizations/{provider}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth14(
            @PathVariable("provider") String provider,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            ref =
                                                                    "#/components/schemas/OAuthAuthorizationRequest")))
                    @org.springframework.web.bind.annotation.RequestBody
                    Object body);

    @Operation(
            operationId = "auth15",
            summary = "본인 재인증 후 소셜 계정 연동",
            security = @SecurityRequirement(name = "bearerAuth"))
    @RequestMapping(
            method = RequestMethod.POST,
            path = "/api/v1/members/me/social-accounts/{provider}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> auth15(
            @PathVariable("provider") String provider,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            ref =
                                                                    "#/components/schemas/SocialLoginRequest")))
                    @org.springframework.web.bind.annotation.RequestBody
                    Object body);
}
