package com.c203.limit.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.admin.dto.request.ChangeAdminPasswordRequest;
import com.c203.limit.domain.admin.entity.AdminAccount;
import com.c203.limit.domain.admin.entity.AdminActionLog;
import com.c203.limit.domain.admin.repository.AdminAccountRepository;
import com.c203.limit.domain.admin.repository.AdminActionLogRepository;
import com.c203.limit.domain.admin.repository.MemberRestrictionRepository;
import com.c203.limit.domain.auth.service.RefreshTokenStore;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.security.JwtTokenProvider;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
