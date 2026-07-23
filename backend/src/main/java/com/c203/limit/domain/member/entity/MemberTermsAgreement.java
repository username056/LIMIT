package com.c203.limit.domain.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "member_terms_agreement",
        uniqueConstraints =
                @UniqueConstraint(columnNames = {"user_id", "terms_code", "terms_version"}))
public class MemberTermsAgreement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "agreement_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "terms_code", nullable = false, length = 30)
    private TermsCode termsCode;

    @Column(name = "terms_version", nullable = false, length = 30)
    private String termsVersion;

    @Column(name = "is_agreed", nullable = false)
    private boolean isAgreed;

    @Column(name = "agreed_at")
    private LocalDateTime agreedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected MemberTermsAgreement() {}

    public static MemberTermsAgreement record(
            Member member, TermsCode termsCode, String termsVersion, boolean isAgreed) {
        MemberTermsAgreement agreement = new MemberTermsAgreement();
        agreement.member = member;
        agreement.termsCode = termsCode;
        agreement.termsVersion = termsVersion;
        agreement.isAgreed = isAgreed;
        agreement.createdAt = LocalDateTime.now();
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

    public LocalDateTime getAgreedAt() {
        return agreedAt;
    }
}
