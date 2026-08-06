package com.c203.limit.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import com.c203.limit.domain.auth.service.AuthService;
import com.c203.limit.domain.auth.service.InMemoryRefreshTokenStore;
import com.c203.limit.domain.auth.service.TermsAgreementService;
import com.c203.limit.domain.member.dto.request.ChangePasswordRequest;
import com.c203.limit.domain.member.dto.request.UpdateMemberRequest;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.seller.service.SellerStatusReader;
import com.c203.limit.domain.product.storage.MediaUrlResolver;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class MemberServiceTests {
    @Mock MemberRepository repository;
    @Mock TermsAgreementService termsAgreementService;
    @Mock SellerStatusReader sellerStatusReader;
    @Mock MediaUrlResolver mediaUrlResolver;
    MemberService service;

    @BeforeEach void setUp() {
        var encoder = new BCryptPasswordEncoder(4);
        var auth = new AuthService(repository, encoder,
                new JwtTokenProvider(new ObjectMapper(), "unit-test-secret-with-at-least-32-bytes", Duration.ofMinutes(30), Duration.ofDays(14)),
                new InMemoryRefreshTokenStore(),
                termsAgreementService,
                sellerStatusReader,
                mediaUrlResolver);
        service = new MemberService(repository, encoder, auth, sellerStatusReader, mediaUrlResolver);
    }

    @Test void masksPhoneInProfile() {
        Member member = Member.createLocal("user@example.com", "encoded", "runner", "01012345678");
        ReflectionTestUtils.setField(member, "id", 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(member));
        assertThat(service.profile(1L).getPhone()).isEqualTo("010****5678");
    }

    @Test void rejectsDuplicatedNicknameOnUpdate() {
        Member member = Member.createLocal("user@example.com", "encoded", "runner", null);
        ReflectionTestUtils.setField(member, "id", 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(member));
        when(repository.existsByNicknameAndIdNot("taken", 1L)).thenReturn(true);
        assertThatThrownBy(() -> service.update(1L, new UpdateMemberRequest("taken", null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NICKNAME_DUPLICATED));
    }

    @Test void reportsSocialMemberWhenPasswordIsMissing() {
        Member member = Member.createSocial("social@example.com", "runner");
        ReflectionTestUtils.setField(member, "id", 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(member));

        var profile = service.profile(1L);

        assertThat(profile.getAuthType()).isEqualTo("SOCIAL");
        assertThat(profile.getPhone()).isNull();
    }

    @Test void keepsPhoneAsIsWhenItIsTooShortToMask() {
        Member member = Member.createLocal("user@example.com", "encoded", "runner", "010123");
        ReflectionTestUtils.setField(member, "id", 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(member));

        assertThat(service.profile(1L).getPhone()).isEqualTo("010123");
    }

    @Test void buildsProfileImageUrlFromStoredKey() {
        Member member = Member.createLocal("user@example.com", "encoded", "runner", null);
        ReflectionTestUtils.setField(member, "id", 1L);
        member.changeProfileImage("members/1/profile/a.jpg");
        when(repository.findById(1L)).thenReturn(Optional.of(member));
        // 저장된 것은 S3 키다. 주소는 읽을 때 만든다.
        when(mediaUrlResolver.resolve("members/1/profile/a.jpg", null))
                .thenReturn("https://cdn/members/1/profile/a.jpg");

        assertThat(service.profile(1L).getProfileImageUrl())
                .isEqualTo("https://cdn/members/1/profile/a.jpg");
    }

    @Test void rejectsProfileLookupForUnknownMember() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.profile(9L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Test void updatesOnlyGivenFieldsAndMasksPhoneInResponse() {
        Member member = Member.createLocal("user@example.com", "encoded", "runner", null);
        ReflectionTestUtils.setField(member, "id", 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(member));

        var response = service.update(1L, new UpdateMemberRequest(null, "01011112222"));

        assertThat(response.getNickname()).isEqualTo("runner");
        assertThat(response.getPhone()).isEqualTo("010****2222");
        verify(repository).flush();
        // 닉네임을 안 보냈으면 중복 검사도 하지 않는다.
        verify(repository, never()).existsByNicknameAndIdNot(any(), any());
    }

    @Test void rejectsPasswordChangeForSocialMember() {
        Member member = Member.createSocial("social@example.com", "runner");
        ReflectionTestUtils.setField(member, "id", 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.changePassword(
                        1L, new ChangePasswordRequest("Password123", "NewPassword456")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SOCIAL_MEMBER_PASSWORD_UNAVAILABLE));
    }

    @Test void rejectsPasswordChangeWhenCurrentPasswordDoesNotMatch() {
        when(repository.findById(1L)).thenReturn(Optional.of(localMember("Password123")));

        assertThatThrownBy(() -> service.changePassword(
                        1L, new ChangePasswordRequest("WrongPassword1", "NewPassword456")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CURRENT_PASSWORD_MISMATCH));
    }

    @Test void rejectsNewPasswordThatBreaksFormatRules() {
        when(repository.findById(1L)).thenReturn(Optional.of(localMember("Password123")));

        assertThatThrownBy(() -> service.changePassword(
                        1L, new ChangePasswordRequest("Password123", "short1")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_PASSWORD_FORMAT));
    }

    @Test void rejectsNewPasswordIdenticalToCurrentOne() {
        when(repository.findById(1L)).thenReturn(Optional.of(localMember("Password123")));

        assertThatThrownBy(() -> service.changePassword(
                        1L, new ChangePasswordRequest("Password123", "Password123")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.SAME_AS_OLD_PASSWORD));
    }

    @Test void storesEncodedNewPasswordOnSuccessfulChange() {
        Member member = localMember("Password123");
        String oldPassword = member.getPassword();
        when(repository.findById(1L)).thenReturn(Optional.of(member));

        service.changePassword(1L, new ChangePasswordRequest("Password123", "NewPassword456"));

        assertThat(member.getPassword()).isNotEqualTo(oldPassword);
        assertThat(new BCryptPasswordEncoder(4).matches("NewPassword456", member.getPassword()))
                .isTrue();
    }

    private Member localMember(String rawPassword) {
        Member member = Member.createLocal(
                "user@example.com", new BCryptPasswordEncoder(4).encode(rawPassword), "runner", null);
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }
}
