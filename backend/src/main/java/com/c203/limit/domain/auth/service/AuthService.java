package com.c203.limit.domain.auth.service;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.c203.limit.domain.auth.dto.request.LoginRequest;
import com.c203.limit.domain.auth.dto.request.SignupRequest;
import com.c203.limit.domain.auth.dto.response.EmailAvailabilityResponse;
import com.c203.limit.domain.auth.dto.response.LoginResponse;
import com.c203.limit.domain.auth.dto.response.NicknameAvailabilityResponse;
import com.c203.limit.domain.auth.dto.response.SignupResponse;
import com.c203.limit.domain.auth.dto.response.TokenResponse;
import com.c203.limit.domain.member.dto.response.MemberSummaryResponse;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.entity.MemberStatus;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.security.JwtTokenProvider;

@Service
public class AuthService {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern NICKNAME = Pattern.compile("^[가-힣a-zA-Z0-9_]{2,20}$");
    private static final Pattern PASSWORD = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,72}$");
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    public AuthService(MemberRepository memberRepository, PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider, RefreshTokenStore refreshTokenStore) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.refreshTokenStore = refreshTokenStore;
    }

    @Transactional(readOnly = true)
    public EmailAvailabilityResponse emailAvailability(String email) {
        String normalized = normalizeEmail(email);
        return new EmailAvailabilityResponse(normalized, !memberRepository.existsByEmailIgnoreCase(normalized));
    }

    @Transactional(readOnly = true)
    public NicknameAvailabilityResponse nicknameAvailability(String nickname) {
        validateNickname(nickname);
        return new NicknameAvailabilityResponse(nickname, !memberRepository.existsByNickname(nickname));
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.getEmail());
        validateNickname(request.getNickname());
        validatePassword(request.getPassword());
        if (memberRepository.existsByEmailIgnoreCase(email)) throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        if (memberRepository.existsByNickname(request.getNickname())) throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        Member member = memberRepository.save(Member.createLocal(email, passwordEncoder.encode(request.getPassword()),
                request.getNickname(), request.getPhone()));
        return new SignupResponse(member.getId(), member.getEmail(), member.getNickname(), member.getStatus().name(),
                Set.of(member.getRole().name()), member.getCreatedAt());
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmailIgnoreCase(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (member.getPassword() == null || !passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (member.getStatus() != MemberStatus.ACTIVE) throw new BusinessException(ErrorCode.MEMBER_NOT_ACTIVE);
        member.recordLogin();
        TokenPair pair = issue(member);
        return new LoginResponse(pair.accessToken(), pair.refreshToken(), "Bearer", tokenProvider.accessTtl().toSeconds(),
                new MemberSummaryResponse(member.getId(), member.getNickname(), Set.of(member.getRole().name())));
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        JwtTokenProvider.TokenClaims claims = tokenProvider.parse(refreshToken, "refresh");
        if (!refreshTokenStore.isValid(claims.tokenId(), claims.subjectId())) {
            throw new BusinessException(ErrorCode.REVOKED_TOKEN);
        }
        Member member = memberRepository.findById(claims.subjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (member.getStatus() != MemberStatus.ACTIVE) throw new BusinessException(ErrorCode.MEMBER_NOT_ACTIVE);
        refreshTokenStore.revoke(claims.tokenId());
        TokenPair pair = issue(member);
        return new TokenResponse(pair.accessToken(), pair.refreshToken(), "Bearer", tokenProvider.accessTtl().toSeconds());
    }

    public void logout(String refreshToken) {
        JwtTokenProvider.TokenClaims claims = tokenProvider.parse(refreshToken, "refresh");
        refreshTokenStore.revoke(claims.tokenId());
    }

    public void revokeAll(Long memberId) { refreshTokenStore.revokeAll(memberId); }

    public TokenResponse issueTokens(Member member) {
        TokenPair pair = issue(member);
        return new TokenResponse(pair.accessToken(), pair.refreshToken(), "Bearer", tokenProvider.accessTtl().toSeconds());
    }

    private TokenPair issue(Member member) {
        Set<String> roles = Set.of(member.getRole().name());
        var access = tokenProvider.issueAccess(member.getId(), "MEMBER", roles);
        var refresh = tokenProvider.issueRefresh(member.getId(), "MEMBER", roles);
        refreshTokenStore.save(refresh.tokenId(), member.getId(), tokenProvider.refreshTtl());
        return new TokenPair(access.value(), refresh.value());
    }
    private String normalizeEmail(String email) {
        if (email == null || !EMAIL.matcher(email.trim()).matches()) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        return email.trim().toLowerCase(Locale.ROOT);
    }
    private void validateNickname(String nickname) {
        if (nickname == null || !NICKNAME.matcher(nickname).matches()) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }
    public void validatePassword(String password) {
        if (password == null || !PASSWORD.matcher(password).matches()) throw new BusinessException(ErrorCode.INVALID_PASSWORD_FORMAT);
    }
    private record TokenPair(String accessToken, String refreshToken) {}
}
