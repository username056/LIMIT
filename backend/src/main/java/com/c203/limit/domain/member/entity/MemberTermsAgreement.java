package com.c203.limit.domain.member.entity;

import java.time.OffsetDateTime;

/** 운영 스키마 migration 전까지 JPA 영속 대상에서 제외한 약관 동의 값 객체다. */
public class MemberTermsAgreement {
    private Long id;

    private Member member;

    private TermsCode termsCode;

    private String termsVersion;

    private boolean isAgreed;

    private OffsetDateTime agreedAt;

    private OffsetDateTime createdAt;

    protected MemberTermsAgreement() {}

    public static MemberTermsAgreement record(
            Member member, TermsCode termsCode, String termsVersion, boolean isAgreed) {
        MemberTermsAgreement agreement = new MemberTermsAgreement();
        agreement.member = member;
        agreement.termsCode = termsCode;
        agreement.termsVersion = termsVersion;
        agreement.isAgreed = isAgreed;
        agreement.createdAt = OffsetDateTime.now();
        agreement.agreedAt = isAgreed ? agreement.createdAt : null;
        return agreement;
    }

    public Long getId() {
        return id;
    }

    public TermsCode getTermsCode() {
        return termsCode;
    }

    public String getTermsVersion() {
        return termsVersion;
    }

    public boolean isAgreed() {
        return isAgreed;
    }

    public OffsetDateTime getAgreedAt() {
        return agreedAt;
    }
}
