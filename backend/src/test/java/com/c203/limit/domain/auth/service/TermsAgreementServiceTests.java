package com.c203.limit.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;

import com.c203.limit.domain.auth.config.TermsProperties;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberTermsAgreementRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TermsAgreementServiceTests {
    @Mock MemberTermsAgreementRepository repository;

    @Test
    void recordsRequiredAndOptionalTermsTogether() {
        var service = new TermsAgreementService(repository, new TermsProperties());
        Member member = Member.createLocal("user@example.com", "encoded", "runner", null);

        service.record(
                member, new TermsAgreementService.TermsConsent(true, true, true, false));

        verify(repository).saveAll(anyList());
    }

    @Test
    void rejectsMissingRequiredConsent() {
        var service = new TermsAgreementService(repository, new TermsProperties());

        assertThatThrownBy(
                        () ->
                                service.validate(
                                        new TermsAgreementService.TermsConsent(
                                                true, false, true, false)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.REQUIRED_TERMS_NOT_ACCEPTED));
    }
}
