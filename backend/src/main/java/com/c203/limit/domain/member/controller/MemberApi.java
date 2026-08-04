package com.c203.limit.domain.member.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Tag(name = "02. 회원")
public interface MemberApi {

    @Operation(
            operationId = "member01",
            summary = "내 회원 정보 조회",
            description = "요청\n권한: MEMBER\nHeader: Authorization: Bearer {accessToken}",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "내 회원 정보 조회 성공",
                content =
                        @Content(
                                schema =
                                        @Schema(
                                                ref =
                                                        "#/components/schemas/MemberProfileResponse"))),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
    })
    @RequestMapping(
            method = RequestMethod.GET,
            path = "/api/v1/members/me",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> member01();

    @Operation(
            operationId = "member02",
            summary = "내 회원 정보 수정",
            description = "요청\n권한: MEMBER\n검증: 변경할 필드만 전달, 닉네임 중복 확인, 이메일·상태·역할 직접 수정 불가",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "내 회원 정보 수정 성공",
                content =
                        @Content(
                                schema =
                                        @Schema(
                                                ref =
                                                        "#/components/schemas/UpdateMemberResponse"))),
        @ApiResponse(responseCode = "400", description = "INVALID_INPUT"),
        @ApiResponse(responseCode = "409", description = "NICKNAME_DUPLICATED")
    })
    @RequestMapping(
            method = RequestMethod.PATCH,
            path = "/api/v1/members/me",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> member02(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = false,
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            ref =
                                                                    "#/components/schemas/UpdateMemberRequest")))
                    @org.springframework.web.bind.annotation.RequestBody(required = false)
                    Object body);

    @Operation(
            operationId = "member03",
            summary = "비밀번호 변경",
            description =
                    "요청\n권한: LOCAL MEMBER\n검증: 기존 비밀번호 일치, 새 비밀번호 정책, 기존 비밀번호와 상이\n처리: password_changed_at 갱신, 기존 Refresh Token 전체 폐기",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "비밀번호 변경 성공"),
        @ApiResponse(responseCode = "400", description = "INVALID_PASSWORD_FORMAT"),
        @ApiResponse(responseCode = "401", description = "CURRENT_PASSWORD_MISMATCH"),
        @ApiResponse(responseCode = "409", description = "SAME_AS_OLD_PASSWORD"),
        @ApiResponse(responseCode = "422", description = "SOCIAL_MEMBER_PASSWORD_UNAVAILABLE")
    })
    @RequestMapping(
            method = RequestMethod.PATCH,
            path = "/api/v1/members/me/password",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> member03(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = false,
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            ref =
                                                                    "#/components/schemas/ChangePasswordRequest")))
                    @org.springframework.web.bind.annotation.RequestBody(required = false)
                    Object body);

    @Operation(
            operationId = "member04",
            summary = "프로필 사진 업로드 URL 발급",
            description =
                    "요청\n권한: MEMBER\n검증: image/jpeg·png·webp, 5MB 이하\n"
                            + "발급받은 presignedUrl로 파일을 PUT 한 뒤 member05로 완료를 알린다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "업로드 URL 발급 성공",
                content =
                        @Content(
                                schema =
                                        @Schema(
                                                ref =
                                                        "#/components/schemas/ProfileImageUploadUrlResponse"))),
        @ApiResponse(responseCode = "400", description = "MEDIA_UPLOAD_INVALID"),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
    })
    @RequestMapping(
            method = RequestMethod.POST,
            path = "/api/v1/members/me/profile-image/upload-url",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> member04(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = false,
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            ref =
                                                                    "#/components/schemas/CreateProfileImageUploadUrlRequest")))
                    @org.springframework.web.bind.annotation.RequestBody(required = false)
                    Object body);

    @Operation(
            operationId = "member05",
            summary = "프로필 사진 등록 완료",
            description =
                    "요청\n권한: MEMBER\n검증: 내 자리에 발급된 objectKey인지, 실제로 업로드됐는지 확인",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "프로필 사진 등록 성공",
                content =
                        @Content(
                                schema =
                                        @Schema(
                                                ref =
                                                        "#/components/schemas/ProfileImageResponse"))),
        @ApiResponse(responseCode = "400", description = "MEDIA_UPLOAD_INVALID"),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
    })
    @RequestMapping(
            method = RequestMethod.PUT,
            path = "/api/v1/members/me/profile-image",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> member05(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = false,
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            ref =
                                                                    "#/components/schemas/CompleteProfileImageRequest")))
                    @org.springframework.web.bind.annotation.RequestBody(required = false)
                    Object body);

    @Operation(
            operationId = "member06",
            summary = "프로필 사진 삭제",
            description = "요청\n권한: MEMBER\n사진을 내리면 화면은 닉네임 첫 글자로 돌아간다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "프로필 사진 삭제 성공",
                content =
                        @Content(
                                schema =
                                        @Schema(
                                                ref =
                                                        "#/components/schemas/ProfileImageResponse"))),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
    })
    @RequestMapping(
            method = RequestMethod.DELETE,
            path = "/api/v1/members/me/profile-image",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> member06();
}
