package com.c203.limit.domain.member.service;

import com.c203.limit.domain.auth.service.AuthService;
import com.c203.limit.domain.member.dto.request.ChangePasswordRequest;
import com.c203.limit.domain.member.dto.request.UpdateMemberRequest;
import com.c203.limit.domain.member.dto.response.MemberProfileResponse;
import com.c203.limit.domain.member.dto.response.UpdateMemberResponse;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.seller.service.SellerStatusReader;
import com.c203.limit.domain.product.storage.MediaUrlResolver;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {
    private static final Logger log = LoggerFactory.getLogger(MemberService.class);
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final SellerStatusReader sellerStatusReader;
    private final MediaUrlResolver mediaUrlResolver;

    public MemberService(
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            AuthService authService,
            SellerStatusReader sellerStatusReader,
            MediaUrlResolver mediaUrlResolver) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.sellerStatusReader = sellerStatusReader;
        this.mediaUrlResolver = mediaUrlResolver;
    }

    @Transactional(readOnly = true)
    public MemberProfileResponse profile(Long memberId) {
        Member member = get(memberId);
        return new MemberProfileResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                maskPhone(member.getPhone()),
                member.getStatus().name(),
                member.getPassword() == null ? "SOCIAL" : "LOCAL",
                sellerStatusReader.rolesFor(memberId),
                sellerStatusReader.statusOf(memberId),
                member.getEmailVerifiedAt(),
                member.getLastLoginAt(),
                member.getCreatedAt(),
                // 저장된 것은 S3 키다. 주소는 읽을 때 만든다 — 버킷·CDN이 바뀌어도 값이 죽지 않는다.
                mediaUrlResolver.resolve(member.getProfileImageKey(), null));
    }

    @Transactional
    public UpdateMemberResponse update(Long memberId, UpdateMemberRequest request) {
        Member member = get(memberId);
        if (request.getNickname() != null
                && memberRepository.existsByNicknameAndIdNot(request.getNickname(), memberId)) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }
        member.updateProfile(request.getNickname(), request.getPhone());
        memberRepository.flush();
        log.info("member profile updated");
        return new UpdateMemberResponse(
                member.getId(),
                member.getNickname(),
                maskPhone(member.getPhone()),
                member.getUpdatedAt());
    }

    @Transactional
    public void changePassword(Long memberId, ChangePasswordRequest request) {
        Member member = get(memberId);
        if (member.getPassword() == null)
            throw new BusinessException(ErrorCode.SOCIAL_MEMBER_PASSWORD_UNAVAILABLE);
        if (!passwordEncoder.matches(request.getCurrentPassword(), member.getPassword())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
        authService.validatePassword(request.getNewPassword());
        if (passwordEncoder.matches(request.getNewPassword(), member.getPassword())) {
            throw new BusinessException(ErrorCode.SAME_AS_OLD_PASSWORD);
        }
        member.changePassword(passwordEncoder.encode(request.getNewPassword()));
        authService.revokeAll(memberId);
        log.info("member password changed and refresh tokens revoked");
    }

    private Member get(Long id) {
        return memberRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
