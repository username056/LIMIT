package com.c203.limit.domain.admin.service;

import com.c203.limit.domain.admin.dto.request.ChangeAdminPasswordRequest;
import com.c203.limit.domain.admin.dto.request.CreateMemberRestrictionRequest;
import com.c203.limit.domain.admin.entity.AdminActionLog;
import com.c203.limit.domain.admin.entity.MemberRestriction;
import com.c203.limit.domain.admin.repository.AdminAccountRepository;
import com.c203.limit.domain.admin.repository.AdminActionLogRepository;
import com.c203.limit.domain.admin.repository.MemberRestrictionRepository;
import com.c203.limit.domain.auth.service.RefreshTokenStore;
import com.c203.limit.domain.auth.service.SessionResult;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.response.PageResponse;
import com.c203.limit.global.security.JwtTokenProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private static final Logger log = LoggerFactory.getLogger(AdminService.class);
    private final AdminAccountRepository adminAccounts;
    private final MemberRepository members;
    private final MemberRestrictionRepository restrictions;
    private final AdminActionLogRepository actionLogs;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    public AdminService(
            AdminAccountRepository adminAccounts,
            MemberRepository members,
            MemberRestrictionRepository restrictions,
            AdminActionLogRepository actionLogs,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            RefreshTokenStore refreshTokenStore) {
        this.adminAccounts = adminAccounts;
        this.members = members;
        this.restrictions = restrictions;
        this.actionLogs = actionLogs;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.refreshTokenStore = refreshTokenStore;
    }

    @Transactional
    public SessionResult<Map<String, Object>> login(String email, String password) {
        var admin =
                adminAccounts
                        .findByEmailIgnoreCase(email.trim())
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (!"ACTIVE".equals(admin.getStatus())
                || !passwordEncoder.matches(password, admin.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        admin.recordLogin();
        return issue(admin);
    }

    @Transactional
    public SessionResult<Map<String, Object>> refresh(String refreshToken) {
        JwtTokenProvider.TokenClaims claims = tokenProvider.parse(refreshToken, "refresh");
        if (!"ADMIN".equals(claims.accountType())
                || !refreshTokenStore.isValid(
                        claims.tokenId(), claims.subjectId(), claims.accountType())) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        var admin =
                adminAccounts
                        .findById(claims.subjectId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_NOT_FOUND));
        if (!"ACTIVE".equals(admin.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        refreshTokenStore.revoke(claims.tokenId());
        return issue(admin);
    }

    @Transactional
    public void changeOwnPassword(Long adminId, ChangeAdminPasswordRequest request) {
        var admin =
                adminAccounts
                        .findById(adminId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_NOT_FOUND));
        if (!passwordEncoder.matches(request.currentPassword(), admin.getPassword())) {
            throw new BusinessException(ErrorCode.ADMIN_CURRENT_PASSWORD_MISMATCH);
        }
        if (request.newPassword() == null
                || request.newPassword().length() < 12
                || request.newPassword().length() > 72) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD_FORMAT);
        }
        if (passwordEncoder.matches(request.newPassword(), admin.getPassword())) {
            throw new BusinessException(ErrorCode.ADMIN_SAME_AS_OLD_PASSWORD);
        }
        admin.changePassword(passwordEncoder.encode(request.newPassword()));
        refreshTokenStore.revokeAll(adminId, "ADMIN");
        audit(adminId, "ADMIN_PASSWORD_CHANGE", "ADMIN_ACCOUNT", adminId, "SELF_SERVICE");
        log.info("Admin password changed and refresh tokens revoked");
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> members(Integer page, Integer size) {
        Page<Member> result = members.findAll(page(page, size));
        return page(result, result.stream().map(this::memberMap).toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> member(Long memberId) {
        return memberMap(getMember(memberId));
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> restrictions(
            Long memberId, Integer page, Integer size) {
        getMember(memberId);
        Page<MemberRestriction> result = restrictions.findAllByMemberId(memberId, page(page, size));
        return page(result, result.stream().map(this::restrictionMap).toList());
    }

    @Transactional
    public Map<String, Object> restrict(
            Long adminId, Long memberId, CreateMemberRestrictionRequest request) {
        Member member = getMember(memberId);
        if (restrictions.existsByMemberIdAndTypeAndStatus(
                memberId, request.restrictionType(), "ACTIVE")) {
            throw new BusinessException(ErrorCode.OVERLAPPING_RESTRICTION);
        }

        MemberRestriction restriction =
                restrictions.save(
                        MemberRestriction.create(
                                member,
                                request.restrictionType(),
                                request.reasonCode(),
                                request.reasonDetail(),
                                request.startsAt(),
                                request.endsAt(),
                                adminId));
        audit(adminId, "MEMBER_RESTRICT", "MEMBER", memberId, request.reasonDetail());
        return restrictionMap(restriction);
    }

    @Transactional
    public Map<String, Object> release(Long adminId, Long restrictionId, String reason) {
        MemberRestriction restriction =
                restrictions
                        .findById(restrictionId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.MEMBER_RESTRICTION_NOT_FOUND));
        restriction.release(adminId, reason);
        audit(
                adminId,
                "MEMBER_RESTRICTION_RELEASE",
                "MEMBER",
                restriction.getMember().getId(),
                reason);
        return restrictionMap(restriction);
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> logs(Integer page, Integer size) {
        Page<AdminActionLog> result =
                actionLogs.findAll(
                        PageRequest.of(
                                page == null ? 0 : Math.max(0, page),
                                size == null ? 20 : Math.min(100, Math.max(1, size)),
                                Sort.by(Sort.Direction.DESC, "createdAt")));
        return page(result, result.stream().map(this::actionLogMap).toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> log(Long actionLogId) {
        return actionLogMap(
                actionLogs
                        .findById(actionLogId)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.ADMIN_ACTION_LOG_NOT_FOUND)));
    }

    private Member getMember(Long memberId) {
        return members.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private SessionResult<Map<String, Object>> issue(
            com.c203.limit.domain.admin.entity.AdminAccount admin) {
        var roles = Set.of(admin.getRole());
        var accessToken = tokenProvider.issueAccess(admin.getId(), "ADMIN", roles);
        var refreshToken = tokenProvider.issueRefresh(admin.getId(), "ADMIN", roles);
        refreshTokenStore.save(
                refreshToken.tokenId(), admin.getId(), "ADMIN", tokenProvider.refreshTtl());
        return new SessionResult<>(
                map(
                        "accessToken", accessToken.value(),
                        "tokenType", "Bearer",
                        "expiresIn", tokenProvider.accessTtl().toSeconds(),
                        "admin",
                                map(
                                        "adminId", admin.getId(),
                                        "name", admin.getName(),
                                        "roles", roles)),
                refreshToken.value());
    }

    private void audit(
            Long adminId, String actionType, String targetType, Long targetId, String reason) {
        actionLogs.save(AdminActionLog.of(adminId, actionType, targetType, targetId, reason));
    }

    private Map<String, Object> memberMap(Member member) {
        return map(
                "memberId", member.getId(),
                "email", maskEmail(member.getEmail()),
                "nickname", member.getNickname(),
                "phone", maskPhone(member.getPhone()),
                "status", member.getStatus().name(),
                "authType", member.getPassword() == null ? "SOCIAL" : "LOCAL",
                "roles", Set.of("MEMBER"),
                "activeRestrictionCount",
                        restrictions.countByMemberIdAndStatus(member.getId(), "ACTIVE"),
                "createdAt", member.getCreatedAt());
    }

    private Map<String, Object> restrictionMap(MemberRestriction restriction) {
        return map(
                "restrictionId", restriction.getId(),
                "memberId", restriction.getMember().getId(),
                "restrictionType", restriction.getType(),
                "status", restriction.getStatus(),
                "reasonCode", restriction.getReasonCode(),
                "reasonDetail", restriction.getReasonDetail(),
                "startsAt", restriction.getStartsAt(),
                "endsAt", restriction.getEndsAt(),
                "createdBy", restriction.getCreatedBy(),
                "releasedBy", restriction.getReleasedBy(),
                "releasedAt", restriction.getReleasedAt(),
                "releaseReason", restriction.getReleaseReason(),
                "createdAt", restriction.getCreatedAt());
    }

    private Map<String, Object> actionLogMap(AdminActionLog actionLog) {
        return map(
                "adminActionLogId", actionLog.getId(),
                "adminId", actionLog.getAdminId(),
                "actionType", actionLog.getActionType(),
                "targetType", actionLog.getTargetType(),
                "targetId", actionLog.getTargetId(),
                "reason", actionLog.getReason(),
                "beforeData", actionLog.getBeforeData(),
                "afterData", actionLog.getAfterData(),
                "ipAddress", actionLog.getIpAddress(),
                "createdAt", actionLog.getCreatedAt());
    }

    private Pageable page(Integer page, Integer size) {
        return PageRequest.of(
                page == null ? 0 : Math.max(0, page),
                size == null ? 20 : Math.min(100, Math.max(1, size)));
    }

    private <T> PageResponse<T> page(Page<?> source, List<T> content) {
        return new PageResponse<>(
                content,
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.hasNext());
    }

    private String maskEmail(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        return at < 1 ? "***" : email.substring(0, 1) + "***" + email.substring(at);
    }

    private String maskPhone(String phone) {
        return phone == null || phone.length() < 7
                ? phone
                : phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put((String) values[index], values[index + 1]);
        }
        return result;
    }
}
