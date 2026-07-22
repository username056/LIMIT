package com.c203.limit.domain.auth.service;

import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class TermsAgreementService {
    public void record(Member member, TermsConsent consent) {
        validate(consent);
    }

    public void validate(TermsConsent consent) {
        if (!consent.serviceAccepted() || !consent.privacyAccepted() || !consent.ageAccepted()) {
            throw new BusinessException(ErrorCode.REQUIRED_TERMS_NOT_ACCEPTED);
        }
    }

    public record TermsConsent(
            boolean serviceAccepted,
            boolean privacyAccepted,
            boolean ageAccepted,
            boolean marketingAccepted) {}
}
