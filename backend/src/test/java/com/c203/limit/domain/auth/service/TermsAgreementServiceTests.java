package com.c203.limit.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class TermsAgreementServiceTests {
    @Test
    void acceptsRequiredAndOptionalTermsTogether() {
        var service = new TermsAgreementService();
        Member member = Member.createLocal("user@example.com", "encoded", "runner", null);

        service.record(
                member, new TermsAgreementService.TermsConsent(true, true, true, false));
    }

    @Test
    void rejectsMissingRequiredConsent() {
        var service = new TermsAgreementService();

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
