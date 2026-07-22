package com.c203.limit.domain.auth.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.auth.service.AuthService;
import com.c203.limit.domain.auth.service.TermsAgreementService;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class MemberBootstrapServiceTests {
    @Mock MemberRepository members;
    @Mock AuthService authService;
    @Mock TermsAgreementService termsAgreementService;

    @Test
    void createsEmailVerifiedMemberWithRequiredTerms() {
        var encoder = new BCryptPasswordEncoder(4);
        var service = service(encoder);
        when(authService.normalizeEmail(" Test@Limit.Local "))
                .thenReturn("test@limit.local");

        assertThat(
                        service.ensureVerifiedMember(
                                " Test@Limit.Local ",
                                "LocalTest123!",
                                "테스트회원",
                                "01000000000"))
                .isTrue();

        var memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(members).save(memberCaptor.capture());
        Member member = memberCaptor.getValue();
        assertThat(member.getEmail()).isEqualTo("test@limit.local");
        assertThat(member.getEmailVerifiedAt()).isNotNull();
        assertThat(encoder.matches("LocalTest123!", member.getPassword())).isTrue();

        var consentCaptor =
                ArgumentCaptor.forClass(TermsAgreementService.TermsConsent.class);
        verify(termsAgreementService).record(org.mockito.ArgumentMatchers.eq(member), consentCaptor.capture());
        assertThat(consentCaptor.getValue().serviceAccepted()).isTrue();
        assertThat(consentCaptor.getValue().privacyAccepted()).isTrue();
        assertThat(consentCaptor.getValue().ageAccepted()).isTrue();
        assertThat(consentCaptor.getValue().marketingAccepted()).isFalse();
    }

    @Test
    void doesNotOverwriteExistingMember() {
        var existing = Member.createLocal("test@limit.local", "encoded", "기존회원", null);
        when(authService.normalizeEmail("test@limit.local")).thenReturn("test@limit.local");
        when(members.findByEmailIgnoreCase("test@limit.local"))
                .thenReturn(Optional.of(existing));

        assertThat(
                        service(new BCryptPasswordEncoder(4))
                                .ensureVerifiedMember(
                                        "test@limit.local",
                                        "LocalTest123!",
                                        "테스트회원",
                                        null))
                .isFalse();
        verify(members, never()).save(org.mockito.ArgumentMatchers.any());
        verify(termsAgreementService, never())
                .record(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsBootstrapPasswordShorterThanTwelveCharacters() {
        when(authService.normalizeEmail("test@limit.local")).thenReturn("test@limit.local");

        assertThatThrownBy(
                        () ->
                                service(new BCryptPasswordEncoder(4))
                                        .ensureVerifiedMember(
                                                "test@limit.local",
                                                "Test1234",
                                                "테스트회원",
                                                null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BASE_INIT_MEMBER_PASSWORD");
    }

    private MemberBootstrapService service(BCryptPasswordEncoder encoder) {
        return new MemberBootstrapService(
                members, encoder, authService, termsAgreementService);
    }
}
