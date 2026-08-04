package com.c203.limit.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.member.dto.request.CompleteProfileImageRequest;
import com.c203.limit.domain.member.dto.request.CreateProfileImageUploadUrlRequest;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.product.storage.MediaObjectStorage;
import com.c203.limit.domain.product.storage.MediaUrlResolver;
import com.c203.limit.domain.product.storage.S3MediaProperties;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.net.URL;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemberProfileImageServiceTests {
    private static final Long MEMBER_ID = 7L;

    @Mock MemberRepository memberRepository;
    @Mock MediaObjectStorage storage;
    @Mock MediaUrlResolver mediaUrlResolver;
    MemberProfileImageService service;

    @BeforeEach void setUp() {
        var properties = new S3MediaProperties(
                "ap-northeast-2", "limit-media", null, false,
                Duration.ofMinutes(5), Duration.ofMinutes(5), null);
        service = new MemberProfileImageService(
                memberRepository, storage, properties, mediaUrlResolver);
    }

    private Member member(String profileImageKey) {
        Member member = Member.createLocal("user@example.com", "encoded", "runner", "01012345678");
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        if (profileImageKey != null) member.changeProfileImage(profileImageKey);
        return member;
    }

    @Test void issuesUploadUrlUnderTheRequesterOwnPrefix() throws Exception {
        when(storage.presignPut(eq("limit-media"), anyString(), eq("image/png"), anyLong(), any()))
                .thenReturn(new URL("https://s3.example.com/put"));

        var response = service.createUploadUrl(
                MEMBER_ID, new CreateProfileImageUploadUrlRequest("image/png", 1024L));

        // 키는 서버가 정한다. 그래서 회원이 남의 자리를 지정할 방법이 없다.
        assertThat(response.objectKey()).startsWith("members/7/profile/").endsWith(".png");
        assertThat(response.presignedUrl()).isEqualTo("https://s3.example.com/put");
        assertThat(response.requiredHeaders()).containsEntry("Content-Type", "image/png");
    }

    @Test void rejectsUnsupportedTypeAndOversizedFile() {
        assertThatThrownBy(() -> service.createUploadUrl(
                        MEMBER_ID, new CreateProfileImageUploadUrlRequest("image/gif", 1024L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDIA_UPLOAD_INVALID);
        assertThatThrownBy(() -> service.createUploadUrl(
                        MEMBER_ID,
                        new CreateProfileImageUploadUrlRequest("image/jpeg", 6L * 1024 * 1024)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDIA_UPLOAD_INVALID);
    }

    // 완료 통보의 키를 손으로 바꿔 남의 사진을 자기 프로필로 등록하는 경로를 막는지 본다.
    @Test void rejectsObjectKeyOutsideTheRequesterPrefix() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member(null)));

        assertThatThrownBy(() -> service.complete(
                        MEMBER_ID, new CompleteProfileImageRequest("members/9/profile/other.jpg")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDIA_UPLOAD_INVALID);
        verify(storage, never()).head(anyString(), anyString());
    }

    // 올리지 않고 완료만 부르면 깨진 사진이 박히므로, 실제로 올라왔는지 확인한다.
    @Test void rejectsCompleteWhenTheObjectIsNotUploaded() {
        String key = "members/7/profile/new.jpg";
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member(null)));
        when(storage.head("limit-media", key)).thenThrow(new IllegalStateException("없음"));

        assertThatThrownBy(() -> service.complete(MEMBER_ID, new CompleteProfileImageRequest(key)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDIA_UPLOAD_INVALID);
    }

    @Test void replacesTheKeyAndDeletesThePreviousFile() {
        String key = "members/7/profile/new.jpg";
        Member member = member("members/7/profile/old.jpg");
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(storage.head("limit-media", key))
                .thenReturn(new MediaObjectStorage.StoredObject(1024L, "image/jpeg"));
        when(mediaUrlResolver.resolve(key, null)).thenReturn("https://cdn/new.jpg");

        var response = service.complete(MEMBER_ID, new CompleteProfileImageRequest(key));

        assertThat(response.profileImageUrl()).isEqualTo("https://cdn/new.jpg");
        assertThat(member.getProfileImageKey()).isEqualTo(key);
        verify(storage).delete("limit-media", "members/7/profile/old.jpg");
    }

    @Test void removeClearsTheKeyAndReturnsNullUrl() {
        Member member = member("members/7/profile/old.jpg");
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        var response = service.remove(MEMBER_ID);

        assertThat(response.profileImageUrl()).isNull();
        assertThat(member.getProfileImageKey()).isNull();
        verify(storage).delete("limit-media", "members/7/profile/old.jpg");
    }

    // 저장소에서 옛 파일을 못 지워도 프로필 변경은 되돌리지 않는다.
    @Test void keepsTheProfileChangeWhenDeletingThePreviousFileFails() {
        Member member = member("members/7/profile/old.jpg");
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        org.mockito.Mockito.doThrow(new IllegalStateException("지우기 실패"))
                .when(storage)
                .delete("limit-media", "members/7/profile/old.jpg");

        var response = service.remove(MEMBER_ID);

        assertThat(response.profileImageUrl()).isNull();
        assertThat(member.getProfileImageKey()).isNull();
    }
}
