package com.c203.limit.domain.auth.service;

import com.c203.limit.domain.auth.config.TermsProperties;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.entity.MemberTermsAgreement;
import com.c203.limit.domain.member.entity.TermsCode;
import com.c203.limit.domain.member.repository.MemberTermsAgreementRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TermsAgreementService {
    private final MemberTermsAgreementRepository repository;
    private final TermsProperties properties;

    public TermsAgreementService(
            MemberTermsAgreementRepository repository, TermsProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public void record(Member member, TermsConsent consent) {
        validate(consent);
        repository.saveAll(
                List.of(
                        agreement(member, TermsCode.SERVICE, properties.getServiceVersion(), true),
                        agreement(member, TermsCode.PRIVACY, properties.getPrivacyVersion(), true),
                        agreement(member, TermsCode.AGE_14, properties.getAgeVersion(), true),
                        agreement(
                                member,
                                TermsCode.MARKETING,
                                properties.getMarketingVersion(),
                                consent.marketingAccepted())));
    }

    public void validate(TermsConsent consent) {
        if (!consent.serviceAccepted() || !consent.privacyAccepted() || !consent.ageAccepted()) {
            throw new BusinessException(ErrorCode.REQUIRED_TERMS_NOT_ACCEPTED);
        }
    }

    private MemberTermsAgreement agreement(
            Member member, TermsCode code, String version, boolean isAgreed) {
        return MemberTermsAgreement.record(member, code, version, isAgreed);
    }

    public record TermsConsent(
            boolean serviceAccepted,
            boolean privacyAccepted,
            boolean ageAccepted,
            boolean marketingAccepted) {}
}
