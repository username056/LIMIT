package com.c203.limit.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.c203.limit.domain.member.dto.request.UpdateMemberRequest;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class MemberServiceTests {
    @Mock MemberRepository repository;
    @Mock TermsAgreementService termsAgreementService;
    MemberService service;

    @BeforeEach void setUp() {
        var encoder = new BCryptPasswordEncoder(4);
        var auth = new AuthService(repository, encoder,
                new JwtTokenProvider(new ObjectMapper(), "unit-test-secret-with-at-least-32-bytes", Duration.ofMinutes(30), Duration.ofDays(14)),
                new InMemoryRefreshTokenStore(),
                termsAgreementService);
        service = new MemberService(repository, encoder, auth);
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
}
