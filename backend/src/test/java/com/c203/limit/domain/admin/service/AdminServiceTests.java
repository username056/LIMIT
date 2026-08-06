package com.c203.limit.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.admin.dto.request.ChangeAdminPasswordRequest;
import com.c203.limit.domain.admin.dto.request.CreateMemberRestrictionRequest;
import com.c203.limit.domain.admin.entity.AdminAccount;
import com.c203.limit.domain.admin.entity.AdminActionLog;
import com.c203.limit.domain.admin.entity.MemberRestriction;
import com.c203.limit.domain.admin.repository.AdminAccountRepository;
import com.c203.limit.domain.admin.repository.AdminActionLogRepository;
import com.c203.limit.domain.admin.repository.MemberRestrictionRepository;
import com.c203.limit.domain.auth.service.RefreshTokenStore;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.security.JwtTokenProvider;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminServiceTests {
    @Mock AdminAccountRepository admins;
    @Mock MemberRepository members;
    @Mock MemberRestrictionRepository restrictions;
    @Mock AdminActionLogRepository logs;
    @Mock JwtTokenProvider tokenProvider;
    @Mock RefreshTokenStore refreshTokens;

    BCryptPasswordEncoder passwordEncoder;
    AdminService service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        service =
                new AdminService(
                        admins,
                        members,
                        restrictions,
                        logs,
                        passwordEncoder,
                        tokenProvider,
                        refreshTokens);
    }

    @Test
    void changesOwnPasswordAndRevokesAdminSessions() {
        AdminAccount admin = admin("OldAdminPassword123!");
        when(admins.findById(1L)).thenReturn(Optional.of(admin));

        service.changeOwnPassword(
                1L,
                new ChangeAdminPasswordRequest(
                        "OldAdminPassword123!", "NewAdminPassword456!"));

        assertThat(passwordEncoder.matches("NewAdminPassword456!", admin.getPassword())).isTrue();
        verify(refreshTokens).revokeAll(1L, "ADMIN");
        verify(logs).save(any());
    }

    @Test
    void rejectsWrongCurrentAdminPassword() {
        AdminAccount admin = admin("OldAdminPassword123!");
        when(admins.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(
                        () ->
                                service.changeOwnPassword(
                                        1L,
                                        new ChangeAdminPasswordRequest(
                                                "WrongPassword123!",
                                                "NewAdminPassword456!")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(
                                                ErrorCode.ADMIN_CURRENT_PASSWORD_MISMATCH));
    }

    @Test
    void rejectsSameAdminPassword() {
        AdminAccount admin = admin("OldAdminPassword123!");
        when(admins.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(
                        () ->
                                service.changeOwnPassword(
                                        1L,
                                        new ChangeAdminPasswordRequest(
                                                "OldAdminPassword123!",
                                                "OldAdminPassword123!")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ADMIN_SAME_AS_OLD_PASSWORD));
    }

    @Test
    void updatesActionLogReasonAndKeepsRevisionLog() {
        AdminActionLog actionLog =
                AdminActionLog.of(3L, "MEMBER_RESTRICT", "MEMBER", 20L, "기존 사유");
        ReflectionTestUtils.setField(actionLog, "id", 7L);
        when(logs.findById(7L)).thenReturn(Optional.of(actionLog));

        var result = service.updateLog(1L, 7L, " 정정된 사유 ");

        assertThat(result.get("reason")).isEqualTo("정정된 사유");
        verify(logs).save(any(AdminActionLog.class));
    }

    @Test
    void issuesAdminSessionOnSuccessfulLogin() {
        AdminAccount account = admin("AdminPassword123!");
        when(admins.findByEmailIgnoreCase("admin@limit.local")).thenReturn(Optional.of(account));
        stubTokenIssuance();

        var result = service.login("  admin@limit.local  ", "AdminPassword123!");

        assertThat(result.refreshToken()).isEqualTo("refresh-value");
        assertThat(result.body())
                .containsEntry("accessToken", "access-value")
                .containsEntry("tokenType", "Bearer")
                .containsEntry("expiresIn", 1800L)
                .containsEntry(
                        "admin",
                        Map.of("adminId", 1L, "name", "admin", "roles", Set.of("SUPER_ADMIN")));
        assertThat(account.getLastLoginAt()).isNotNull();
        verify(refreshTokens).save("refresh-id", 1L, "ADMIN", Duration.ofDays(14));
    }

    @Test
    void rejectsLoginForUnknownAdminEmail() {
        when(admins.findByEmailIgnoreCase("ghost@limit.local")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("ghost@limit.local", "AdminPassword123!"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void rejectsLoginWithWrongAdminPassword() {
        when(admins.findByEmailIgnoreCase("admin@limit.local"))
                .thenReturn(Optional.of(admin("AdminPassword123!")));

        assertThatThrownBy(() -> service.login("admin@limit.local", "WrongPassword123!"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void rejectsLoginForSuspendedAdminAccount() {
        AdminAccount account = admin("AdminPassword123!");
        account.updateAccess(null, "SUSPENDED");
        when(admins.findByEmailIgnoreCase("admin@limit.local")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.login("admin@limit.local", "AdminPassword123!"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void rotatesRefreshTokenOnAdminRefresh() {
        AdminAccount account = admin("AdminPassword123!");
        when(tokenProvider.parse("old-refresh", "refresh")).thenReturn(adminClaims());
        when(refreshTokens.isValid("old-token-id", 1L, "ADMIN")).thenReturn(true);
        when(admins.findById(1L)).thenReturn(Optional.of(account));
        stubTokenIssuance();

        var result = service.refresh("old-refresh");

        assertThat(result.refreshToken()).isEqualTo("refresh-value");
        verify(refreshTokens).revoke("old-token-id");
        verify(refreshTokens).save("refresh-id", 1L, "ADMIN", Duration.ofDays(14));
    }

    @Test
    void rejectsAdminRefreshWhenAccountTypeIsNotAdmin() {
        when(tokenProvider.parse("member-refresh", "refresh"))
                .thenReturn(
                        new JwtTokenProvider.TokenClaims(
                                "token-id", 1L, "MEMBER", Set.of("MEMBER")));

        assertThatThrownBy(() -> service.refresh("member-refresh"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    @Test
    void rejectsAdminRefreshWhenTokenIsRevoked() {
        when(tokenProvider.parse("old-refresh", "refresh")).thenReturn(adminClaims());
        when(refreshTokens.isValid("old-token-id", 1L, "ADMIN")).thenReturn(false);

        assertThatThrownBy(() -> service.refresh("old-refresh"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    @Test
    void rejectsAdminRefreshWhenAccountIsGone() {
        when(tokenProvider.parse("old-refresh", "refresh")).thenReturn(adminClaims());
        when(refreshTokens.isValid("old-token-id", 1L, "ADMIN")).thenReturn(true);
        when(admins.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh("old-refresh"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ADMIN_NOT_FOUND));
    }

    @Test
    void rejectsAdminRefreshForSuspendedAccount() {
        AdminAccount account = admin("AdminPassword123!");
        account.updateAccess(null, "SUSPENDED");
        when(tokenProvider.parse("old-refresh", "refresh")).thenReturn(adminClaims());
        when(refreshTokens.isValid("old-token-id", 1L, "ADMIN")).thenReturn(true);
        when(admins.findById(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.refresh("old-refresh"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void rejectsPasswordChangeForUnknownAdmin() {
        when(admins.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.changeOwnPassword(
                                        1L,
                                        new ChangeAdminPasswordRequest(
                                                "OldAdminPassword123!", "NewAdminPassword456!")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ADMIN_NOT_FOUND));
    }

    @Test
    void rejectsNewAdminPasswordShorterThanTwelveCharacters() {
        when(admins.findById(1L)).thenReturn(Optional.of(admin("OldAdminPassword123!")));

        assertThatThrownBy(
                        () ->
                                service.changeOwnPassword(
                                        1L,
                                        new ChangeAdminPasswordRequest(
                                                "OldAdminPassword123!", "Short12345!")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_PASSWORD_FORMAT));
    }

    @Test
    void rejectsNewAdminPasswordLongerThanSeventyTwoCharacters() {
        when(admins.findById(1L)).thenReturn(Optional.of(admin("OldAdminPassword123!")));

        assertThatThrownBy(
                        () ->
                                service.changeOwnPassword(
                                        1L,
                                        new ChangeAdminPasswordRequest(
                                                "OldAdminPassword123!", "a".repeat(73))))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_PASSWORD_FORMAT));
    }

    @Test
    void masksEmailAndPhoneWhenListingMembers() {
        Member member = member("user@example.com", "01012345678");
        when(members.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(member), PageRequest.of(0, 20), 1));
        when(restrictions.countByMemberIdAndStatus(5L, "ACTIVE")).thenReturn(2L);

        var result = service.members(0, 20);

        assertThat(result.getContent()).hasSize(1);
        Map<String, Object> row = result.getContent().get(0);
        assertThat(row)
                .containsEntry("memberId", 5L)
                .containsEntry("email", "u***@example.com")
                .containsEntry("phone", "010****5678")
                .containsEntry("status", "ACTIVE")
                .containsEntry("authType", "LOCAL")
                .containsEntry("activeRestrictionCount", 2L);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.isHasNext()).isFalse();
    }

    @Test
    void clampsPageAndSizeToAllowedBounds() {
        when(members.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        service.members(-5, 500);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(members).findAll(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void usesDefaultPageAndSizeWhenParametersAreNull() {
        when(members.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.members(null, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(members).findAll(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void keepsUnmaskableEmailAndShortPhoneAsIs() {
        Member member = member("@example.com", "01012");
        when(members.findById(5L)).thenReturn(Optional.of(member));
        when(restrictions.countByMemberIdAndStatus(5L, "ACTIVE")).thenReturn(0L);

        var result = service.member(5L);

        assertThat(result).containsEntry("email", "***").containsEntry("phone", "01012");
    }

    @Test
    void reportsSocialAuthTypeForMemberWithoutPassword() {
        Member member = Member.createSocial("social@example.com", "소셜회원");
        ReflectionTestUtils.setField(member, "id", 5L);
        when(members.findById(5L)).thenReturn(Optional.of(member));
        when(restrictions.countByMemberIdAndStatus(5L, "ACTIVE")).thenReturn(0L);

        assertThat(service.member(5L))
                .containsEntry("authType", "SOCIAL")
                .containsEntry("phone", null);
    }

    @Test
    void rejectsMemberLookupWhenMemberIsMissing() {
        when(members.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.member(5L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    void listsRestrictionsOfExistingMember() {
        Member member = member("user@example.com", "01012345678");
        MemberRestriction restriction = restriction(member);
        when(members.findById(5L)).thenReturn(Optional.of(member));
        when(restrictions.findAllByMemberId(org.mockito.ArgumentMatchers.eq(5L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(restriction), PageRequest.of(0, 20), 1));

        var result = service.restrictions(5L, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0))
                .containsEntry("restrictionId", 3L)
                .containsEntry("memberId", 5L)
                .containsEntry("restrictionType", "PURCHASE")
                .containsEntry("status", "ACTIVE");
    }

    @Test
    void rejectsRestrictionListingForUnknownMember() {
        when(members.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restrictions(5L, 0, 20))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    void createsRestrictionAndWritesAuditLog() {
        Member member = member("user@example.com", "01012345678");
        MemberRestriction saved = restriction(member);
        CreateMemberRestrictionRequest request =
                new CreateMemberRestrictionRequest(
                        "PURCHASE",
                        "MACRO_USE",
                        "비정상 반복 요청",
                        LocalDateTime.of(2026, 7, 22, 14, 0),
                        LocalDateTime.of(2026, 7, 29, 14, 0));
        when(members.findById(5L)).thenReturn(Optional.of(member));
        when(restrictions.existsByMemberIdAndTypeAndStatus(5L, "PURCHASE", "ACTIVE"))
                .thenReturn(false);
        when(restrictions.save(any(MemberRestriction.class))).thenReturn(saved);

        var result = service.restrict(1L, 5L, request);

        assertThat(result).containsEntry("restrictionId", 3L).containsEntry("status", "ACTIVE");
        verify(logs).save(any(AdminActionLog.class));
    }

    @Test
    void rejectsOverlappingActiveRestriction() {
        Member member = member("user@example.com", "01012345678");
        CreateMemberRestrictionRequest request =
                new CreateMemberRestrictionRequest(
                        "PURCHASE",
                        "MACRO_USE",
                        "비정상 반복 요청",
                        LocalDateTime.of(2026, 7, 22, 14, 0),
                        LocalDateTime.of(2026, 7, 29, 14, 0));
        when(members.findById(5L)).thenReturn(Optional.of(member));
        when(restrictions.existsByMemberIdAndTypeAndStatus(5L, "PURCHASE", "ACTIVE"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.restrict(1L, 5L, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.OVERLAPPING_RESTRICTION));
    }

    @Test
    void releasesRestrictionAndWritesAuditLog() {
        Member member = member("user@example.com", "01012345678");
        MemberRestriction restriction = restriction(member);
        when(restrictions.findById(3L)).thenReturn(Optional.of(restriction));

        var result = service.release(1L, 3L, "오탐 확인");

        assertThat(result)
                .containsEntry("status", "RELEASED")
                .containsEntry("releasedBy", 1L)
                .containsEntry("releaseReason", "오탐 확인");
        verify(logs).save(any(AdminActionLog.class));
    }

    @Test
    void rejectsReleaseOfUnknownRestriction() {
        when(restrictions.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.release(1L, 3L, "오탐 확인"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.MEMBER_RESTRICTION_NOT_FOUND));
    }

    @Test
    void sortsActionLogsByCreatedAtDescending() {
        AdminActionLog actionLog = AdminActionLog.of(1L, "MEMBER_RESTRICT", "MEMBER", 5L, "사유");
        ReflectionTestUtils.setField(actionLog, "id", 7L);
        when(logs.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(actionLog), PageRequest.of(0, 20), 1));

        var result = service.logs(null, null);

        assertThat(result.getContent().get(0))
                .containsEntry("adminActionLogId", 7L)
                .containsEntry("actionType", "MEMBER_RESTRICT")
                .containsEntry("targetId", 5L);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(logs).findAll(captor.capture());
        assertThat(captor.getValue().getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void returnsSingleActionLog() {
        AdminActionLog actionLog = AdminActionLog.of(1L, "MEMBER_RESTRICT", "MEMBER", 5L, "사유");
        ReflectionTestUtils.setField(actionLog, "id", 7L);
        when(logs.findById(7L)).thenReturn(Optional.of(actionLog));

        assertThat(service.log(7L)).containsEntry("adminActionLogId", 7L);
    }

    @Test
    void rejectsUnknownActionLogLookup() {
        when(logs.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.log(7L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ADMIN_ACTION_LOG_NOT_FOUND));
    }

    @Test
    void rejectsUnknownActionLogUpdate() {
        when(logs.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateLog(1L, 7L, "정정된 사유"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ADMIN_ACTION_LOG_NOT_FOUND));
    }

    private void stubTokenIssuance() {
        Set<String> roles = Set.of("SUPER_ADMIN");
        when(tokenProvider.issueAccess(1L, "ADMIN", roles))
                .thenReturn(
                        new JwtTokenProvider.IssuedToken(
                                "access-value", "access-id", Instant.EPOCH));
        when(tokenProvider.issueRefresh(1L, "ADMIN", roles))
                .thenReturn(
                        new JwtTokenProvider.IssuedToken(
                                "refresh-value", "refresh-id", Instant.EPOCH));
        when(tokenProvider.refreshTtl()).thenReturn(Duration.ofDays(14));
        when(tokenProvider.accessTtl()).thenReturn(Duration.ofMinutes(30));
    }

    private JwtTokenProvider.TokenClaims adminClaims() {
        return new JwtTokenProvider.TokenClaims(
                "old-token-id", 1L, "ADMIN", Set.of("SUPER_ADMIN"));
    }

    private Member member(String email, String phone) {
        Member member = Member.createLocal(email, "encoded", "회원", phone);
        ReflectionTestUtils.setField(member, "id", 5L);
        return member;
    }

    private MemberRestriction restriction(Member member) {
        MemberRestriction restriction =
                MemberRestriction.create(
                        member,
                        "PURCHASE",
                        "MACRO_USE",
                        "비정상 반복 요청",
                        LocalDateTime.of(2026, 7, 22, 14, 0),
                        LocalDateTime.of(2026, 7, 29, 14, 0),
                        1L);
        ReflectionTestUtils.setField(restriction, "id", 3L);
        return restriction;
    }

    private AdminAccount admin(String password) {
        AdminAccount account =
                AdminAccount.createInitial(
                        "admin@limit.local",
                        passwordEncoder.encode(password),
                        "admin",
                        "SUPER_ADMIN");
        ReflectionTestUtils.setField(account, "id", 1L);
        return account;
    }
}
