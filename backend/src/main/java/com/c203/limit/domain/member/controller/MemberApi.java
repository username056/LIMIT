package com.c203.limit.domain.member.controller;

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

@Tag(name = "02. 회원")
public interface MemberApi {

    @Operation(operationId = "member01", summary = "내 회원 정보 조회", description = "요청\n권한: MEMBER\nHeader: Authorization: Bearer {accessToken}\n\n응답\n200 OK\n{\"data\":{\"memberId\":1,\"email\":\"user@example.com\",\"nickname\":\"openrunner\",\"phone\":\"010****5678\",\"status\":\"ACTIVE\",\"authType\":\"LOCAL\",\"roles\":[\"BUYER\"],\"emailVerifiedAt\":null,\"lastLoginAt\":\"2026-07-16T10:50:00+09:00\",\"createdAt\":\"2026-07-01T10:00:00+09:00\"}}\n오류: 401 UNAUTHORIZED", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/MemberProfileResponse"))),
        @ApiResponse(responseCode = "401", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.GET, path = "/api/v1/members/me", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> member01();
    @Operation(operationId = "member02", summary = "내 회원 정보 수정", description = "요청\n권한: MEMBER\nBody:\n{\"nickname\":\"newNickname\",\"phone\":\"01098765432\"}\n검증: 변경할 필드만 전달, 닉네임 중복 확인, 이메일·상태·역할 직접 수정 불가\n\n응답\n200 OK\n{\"data\":{\"memberId\":1,\"nickname\":\"newNickname\",\"phone\":\"010****5432\",\"updatedAt\":\"2026-07-16T11:10:00+09:00\"}}\n오류: 400 INVALID_INPUT, 409 NICKNAME_DUPLICATED", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "명세 응답", content = @Content(schema = @Schema(ref = "#/components/schemas/UpdateMemberResponse"))),
        @ApiResponse(responseCode = "400", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "409", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.PATCH, path = "/api/v1/members/me", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> member02(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/UpdateMemberRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
    @Operation(operationId = "member03", summary = "비밀번호 변경", description = "요청\n권한: LOCAL MEMBER\nBody:\n{\"currentPassword\":\"Password123!\",\"newPassword\":\"NewPassword456!\"}\n검증: 기존 비밀번호 일치, 새 비밀번호 정책, 기존 비밀번호와 상이\n처리: password_changed_at 갱신, 기존 Refresh Token 전체 폐기\n\n응답\n204 No Content\n오류: 400 INVALID_PASSWORD_FORMAT, 401 CURRENT_PASSWORD_MISMATCH, 409 SAME_AS_OLD_PASSWORD, 422 SOCIAL_MEMBER_PASSWORD_UNAVAILABLE", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "명세 응답"),
        @ApiResponse(responseCode = "400", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "401", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "409", description = "명세 오류 응답"),
        @ApiResponse(responseCode = "422", description = "명세 오류 응답")
    })
    @RequestMapping(method = RequestMethod.PATCH, path = "/api/v1/members/me/password", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> member03(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(schema = @Schema(ref = "#/components/schemas/ChangePasswordRequest"))) @org.springframework.web.bind.annotation.RequestBody(required = false) Object body
    );
}
