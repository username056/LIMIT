package com.c203.limit.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.c203.limit.domain.member.dto.request.ChangePasswordRequest;
import com.c203.limit.domain.member.dto.request.CompleteProfileImageRequest;
import com.c203.limit.domain.member.dto.request.CreateProfileImageUploadUrlRequest;
import com.c203.limit.domain.member.dto.request.UpdateMemberRequest;
import com.c203.limit.domain.member.dto.response.MemberProfileResponse;
import com.c203.limit.domain.member.dto.response.ProfileImageResponse;
import com.c203.limit.domain.member.dto.response.ProfileImageUploadUrlResponse;
import com.c203.limit.domain.member.dto.response.UpdateMemberResponse;
import com.c203.limit.domain.member.service.MemberProfileImageService;
import com.c203.limit.domain.member.service.MemberService;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class MemberControllerTests {
    private static final Long MEMBER_ID = 7L;

    @Mock MemberService memberService;
    @Mock MemberProfileImageService profileImageService;
    @Mock CurrentUser currentUser;

    MemberController controller;

    @BeforeEach
    void setUp() {
        controller = new MemberController(
                memberService, profileImageService, currentUser, new ObjectMapper());
    }

    @Test
    void returnsProfileOfCurrentMember() {
        MemberProfileResponse profile = profile();
        when(currentUser.memberId()).thenReturn(MEMBER_ID);
        when(memberService.profile(MEMBER_ID)).thenReturn(profile);

        ResponseEntity<Void> response = controller.member01();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(response).data()).isSameAs(profile);
        assertThat(body(response).meta()).isNull();
    }

    @Test
    void passesNicknameAndPhoneToUpdate() {
        when(currentUser.memberId()).thenReturn(MEMBER_ID);
        when(memberService.update(eq(MEMBER_ID),
                        any(UpdateMemberRequest.class)))
                .thenReturn(updated());

        ResponseEntity<Void> response =
                controller.member02(Map.of("nickname", "newNickname", "phone", "01098765432"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ArgumentCaptor<UpdateMemberRequest> captor =
                ArgumentCaptor.forClass(UpdateMemberRequest.class);
        verify(memberService).update(eq(MEMBER_ID), captor.capture());
        assertThat(captor.getValue().getNickname()).isEqualTo("newNickname");
        assertThat(captor.getValue().getPhone()).isEqualTo("01098765432");
    }

    @Test
    void treatsMissingAndNullFieldsAsUnchangedOnUpdate() {
        Map<String, Object> body = new HashMap<>();
        body.put("phone", null);
        when(currentUser.memberId()).thenReturn(MEMBER_ID);
        when(memberService.update(eq(MEMBER_ID),
                        any(UpdateMemberRequest.class)))
                .thenReturn(updated());

        controller.member02(body);

        ArgumentCaptor<UpdateMemberRequest> captor =
                ArgumentCaptor.forClass(UpdateMemberRequest.class);
        verify(memberService).update(eq(MEMBER_ID), captor.capture());
        assertThat(captor.getValue().getNickname()).isNull();
        assertThat(captor.getValue().getPhone()).isNull();
    }

    @Test
    void rejectsUpdateWithoutBody() {
        assertThatThrownBy(() -> controller.member02(null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verifyNoInteractions(memberService, currentUser);
    }

    @Test
    void returnsNoContentAfterPasswordChange() {
        when(currentUser.memberId()).thenReturn(MEMBER_ID);

        ResponseEntity<Void> response = controller.member03(
                Map.of("currentPassword", "Password123!", "newPassword", "NewPassword456!"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        ArgumentCaptor<ChangePasswordRequest> captor =
                ArgumentCaptor.forClass(ChangePasswordRequest.class);
        verify(memberService).changePassword(
                eq(MEMBER_ID), captor.capture());
        assertThat(captor.getValue().getCurrentPassword()).isEqualTo("Password123!");
        assertThat(captor.getValue().getNewPassword()).isEqualTo("NewPassword456!");
    }

    @Test
    void rejectsPasswordChangeWithMissingNewPassword() {
        assertThatThrownBy(() -> controller.member03(Map.of("currentPassword", "Password123!")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verifyNoInteractions(memberService);
    }

    @Test
    void rejectsPasswordChangeWithBlankCurrentPassword() {
        assertThatThrownBy(() -> controller.member03(
                        Map.of("currentPassword", "   ", "newPassword", "NewPassword456!")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verifyNoInteractions(memberService);
    }

    @Test
    void passesContentTypeAndFileSizeToUploadUrlCreation() {
        ProfileImageUploadUrlResponse uploadUrl = uploadUrl();
        when(currentUser.memberId()).thenReturn(MEMBER_ID);
        when(profileImageService.createUploadUrl(eq(MEMBER_ID),
                        any(CreateProfileImageUploadUrlRequest.class)))
                .thenReturn(uploadUrl);

        ResponseEntity<Void> response =
                controller.member04(Map.of("contentType", "image/jpeg", "fileSize", 2048));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(response).data()).isSameAs(uploadUrl);
        ArgumentCaptor<CreateProfileImageUploadUrlRequest> captor =
                ArgumentCaptor.forClass(CreateProfileImageUploadUrlRequest.class);
        verify(profileImageService).createUploadUrl(
                eq(MEMBER_ID), captor.capture());
        assertThat(captor.getValue().getContentType()).isEqualTo("image/jpeg");
        assertThat(captor.getValue().getFileSize()).isEqualTo(2048L);
    }

    @Test
    void rejectsUploadUrlRequestWithoutContentType() {
        assertThatThrownBy(() -> controller.member04(Map.of("fileSize", 2048)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verifyNoInteractions(profileImageService);
    }

    @Test
    void rejectsUploadUrlRequestWithNonNumericFileSize() {
        assertThatThrownBy(() -> controller.member04(
                        Map.of("contentType", "image/jpeg", "fileSize", "big")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verifyNoInteractions(profileImageService);
    }

    @Test
    void rejectsUploadUrlRequestWithoutFileSize() {
        assertThatThrownBy(() -> controller.member04(Map.of("contentType", "image/jpeg")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verifyNoInteractions(profileImageService);
    }

    @Test
    void passesObjectKeyToProfileImageCompletion() {
        ProfileImageResponse completed = new ProfileImageResponse("https://cdn/profile.jpg");
        when(currentUser.memberId()).thenReturn(MEMBER_ID);
        when(profileImageService.complete(eq(MEMBER_ID),
                        any(CompleteProfileImageRequest.class)))
                .thenReturn(completed);

        ResponseEntity<Void> response =
                controller.member05(Map.of("objectKey", "members/7/profile/abc.jpg"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(response).data()).isSameAs(completed);
        ArgumentCaptor<CompleteProfileImageRequest> captor =
                ArgumentCaptor.forClass(CompleteProfileImageRequest.class);
        verify(profileImageService).complete(
                eq(MEMBER_ID), captor.capture());
        assertThat(captor.getValue().getObjectKey()).isEqualTo("members/7/profile/abc.jpg");
    }

    @Test
    void rejectsProfileImageCompletionWithBlankObjectKey() {
        assertThatThrownBy(() -> controller.member05(Map.of("objectKey", "")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verifyNoInteractions(profileImageService);
    }

    @Test
    void returnsClearedProfileImageAfterRemoval() {
        ProfileImageResponse removed = new ProfileImageResponse(null);
        when(currentUser.memberId()).thenReturn(MEMBER_ID);
        when(profileImageService.remove(MEMBER_ID)).thenReturn(removed);

        ResponseEntity<Void> response = controller.member06();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(response).data()).isSameAs(removed);
    }

    private ApiResponse<?> body(ResponseEntity<Void> response) {
        return (ApiResponse<?>) (Object) response.getBody();
    }

    private MemberProfileResponse profile() {
        return new MemberProfileResponse(
                MEMBER_ID, "user@example.com", "openrunner", "010****5678", "ACTIVE", "LOCAL",
                Set.of("MEMBER"), null, null, null, LocalDateTime.of(2026, 7, 1, 10, 0), null);
    }

    private UpdateMemberResponse updated() {
        return new UpdateMemberResponse(
                MEMBER_ID, "newNickname", "010****5432", LocalDateTime.of(2026, 7, 16, 11, 10));
    }

    private ProfileImageUploadUrlResponse uploadUrl() {
        return new ProfileImageUploadUrlResponse(
                "members/7/profile/abc.jpg",
                "https://s3/presigned",
                OffsetDateTime.of(2026, 7, 16, 11, 10, 0, 0, ZoneOffset.UTC),
                Map.of("Content-Type", "image/jpeg"));
    }
}
