package com.c203.limit.domain.auth.bootstrap;

import com.c203.limit.domain.auth.service.AuthService;
import com.c203.limit.domain.auth.service.TermsAgreementService;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberBootstrapService {
    private static final int MINIMUM_BOOTSTRAP_PASSWORD_LENGTH = 12;

    private final MemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final TermsAgreementService termsAgreementService;

    public MemberBootstrapService(
            MemberRepository members,
            PasswordEncoder passwordEncoder,
            AuthService authService,
            TermsAgreementService termsAgreementService) {
        this.members = members;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.termsAgreementService = termsAgreementService;
    }

    @Transactional
    public boolean ensureVerifiedMember(
            String email, String password, String nickname, String phone) {
        String normalizedEmail = authService.normalizeEmail(email);
        if (members.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            return false;
        }
        validate(password, nickname);
        if (members.existsByNickname(nickname)) {
            throw new IllegalStateException("BASE_INIT_MEMBER_NICKNAME is already in use");
        }

        Member member =
                Member.createLocal(
                        normalizedEmail, passwordEncoder.encode(password), nickname, phone, false);
        member.verifyEmail();
        members.save(member);
        termsAgreementService.record(
                member, new TermsAgreementService.TermsConsent(true, true, true, false));
        return true;
    }

    private void validate(String password, String nickname) {
        authService.validatePassword(password);
        authService.validateNickname(nickname);
        if (password.length() < MINIMUM_BOOTSTRAP_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "BASE_INIT_MEMBER_PASSWORD must contain at least 12 characters");
        }
    }
}
