package com.c203.limit.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.admin.dto.request.CreateAdminAccountRequest;
import com.c203.limit.domain.admin.dto.request.UpdateAdminAccountAccessRequest;
import com.c203.limit.domain.admin.dto.response.AdminAccountResponse;
import com.c203.limit.domain.admin.entity.AdminAccount;
import com.c203.limit.domain.admin.entity.AdminActionLog;
import com.c203.limit.domain.admin.repository.AdminAccountRepository;
import com.c203.limit.domain.admin.repository.AdminActionLogRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import com.c203.limit.global.response.PageResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminAccountManagementServiceTests {
    @Mock AdminAccountRepository accounts;
    @Mock AdminActionLogRepository logs;
    AdminAccountManagementService service;

    @BeforeEach
    void setUp() {
        service =
                new AdminAccountManagementService(
                        accounts, logs, new BCryptPasswordEncoder(4));
    }

    @Test
    void protectsLastActiveSuperAdmin() {
        AdminAccount account =
                AdminAccount.createInitial(
                        "root@limit.local", "encoded-password", "root", "SUPER_ADMIN");
        ReflectionTestUtils.setField(account, "id", 1L);
        when(accounts.findById(1L)).thenReturn(Optional.of(account));
        when(accounts.findActiveSuperAdminsForUpdate()).thenReturn(List.of(account));

        assertThatThrownBy(
                        () ->
                                service.update(
                                        1L,
                                        1L,
                                        new UpdateAdminAccountAccessRequest(
                                                "OPERATOR", "ACTIVE")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LAST_SUPER_ADMIN));
    }

    @Test
    void allowsDemotionWhenAnotherActiveSuperAdminIsLocked() {
        AdminAccount target =
                AdminAccount.createInitial(
                        "target@limit.local", "encoded-password", "target", "SUPER_ADMIN");
        AdminAccount remaining =
                AdminAccount.createInitial(
                        "remaining@limit.local", "encoded-password", "remaining", "SUPER_ADMIN");
        ReflectionTestUtils.setField(target, "id", 1L);
        ReflectionTestUtils.setField(remaining, "id", 2L);
        when(accounts.findById(1L)).thenReturn(Optional.of(target));
        when(accounts.findActiveSuperAdminsForUpdate()).thenReturn(List.of(target, remaining));

        service.update(
                2L,
                1L,
                new UpdateAdminAccountAccessRequest("OPERATOR", "ACTIVE"));

        assertThat(target.getRole()).isEqualTo("OPERATOR");
    }

    @Test
    void listAppliesDefaultPagingAndNewestFirstSortWhenArgumentsAreNull() {
        AdminAccount account = account(7L, "listed@limit.local", "OPERATOR");
        when(accounts.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(account), PageRequest.of(0, 20), 1));

        PageResponse<AdminAccountResponse> response = service.list(null, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(accounts).findAll(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        assertThat(captor.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        assertThat(response.getContent()).singleElement().satisfies(item -> {
            assertThat(item.adminId()).isEqualTo(7L);
            assertThat(item.email()).isEqualTo("listed@limit.local");
            assertThat(item.role()).isEqualTo("OPERATOR");
            assertThat(item.status()).isEqualTo("ACTIVE");
        });
        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(20);
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getTotalPages()).isEqualTo(1);
        assertThat(response.isHasNext()).isFalse();
    }

    @Test
    void listClampsNegativePageAndOutOfRangeSize() {
        when(accounts.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.list(-4, 500);
        service.list(3, 0);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(accounts, times(2)).findAll(captor.capture());
        assertThat(captor.getAllValues().get(0).getPageNumber()).isZero();
        assertThat(captor.getAllValues().get(0).getPageSize()).isEqualTo(100);
        assertThat(captor.getAllValues().get(1).getPageNumber()).isEqualTo(3);
        assertThat(captor.getAllValues().get(1).getPageSize()).isEqualTo(1);
    }

    @Test
    void createNormalizesEmailAndNameThenEncodesPasswordAndAudits() {
        when(accounts.findByEmailIgnoreCase("operator@limit.local"))
                .thenReturn(Optional.empty());
        when(accounts.save(any(AdminAccount.class))).thenAnswer(invocation -> {
            AdminAccount saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 11L);
            return saved;
        });

        AdminAccountResponse response =
                service.create(
                        900L,
                        new CreateAdminAccountRequest(
                                "  Operator@Limit.Local  ",
                                "sufficiently-long-password",
                                "  운영자  ",
                                "OPERATOR"));

        assertThat(response.adminId()).isEqualTo(11L);
        assertThat(response.email()).isEqualTo("operator@limit.local");
        assertThat(response.name()).isEqualTo("운영자");
        assertThat(response.role()).isEqualTo("OPERATOR");
        assertThat(response.status()).isEqualTo("ACTIVE");

        ArgumentCaptor<AdminAccount> savedCaptor =
                ArgumentCaptor.forClass(AdminAccount.class);
        verify(accounts).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getPassword())
                .isNotEqualTo("sufficiently-long-password")
                .startsWith("$2a$04$");
        verify(logs).save(any(AdminActionLog.class));
    }

    @Test
    void createRejectsEmailWithoutDomainSeparator() {
        assertThatThrownBy(
                        () ->
                                service.create(
                                        900L,
                                        new CreateAdminAccountRequest(
                                                "operator-limit.local",
                                                "sufficiently-long-password",
                                                "운영자",
                                                "OPERATOR")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(
                        () ->
                                service.create(
                                        900L,
                                        new CreateAdminAccountRequest(
                                                null,
                                                "sufficiently-long-password",
                                                "운영자",
                                                "OPERATOR")))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(accounts, logs);
    }

    @Test
    void createRejectsRoleOutsideAllowedSet() {
        assertThatThrownBy(
                        () ->
                                service.create(
                                        900L,
                                        new CreateAdminAccountRequest(
                                                "operator@limit.local",
                                                "sufficiently-long-password",
                                                "운영자",
                                                "INSPECTOR")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_ADMIN_ROLE));
        verifyNoInteractions(accounts, logs);
    }

    @Test
    void createRejectsMissingOrTooShortPassword() {
        assertThatThrownBy(
                        () ->
                                service.create(
                                        900L,
                                        new CreateAdminAccountRequest(
                                                "operator@limit.local",
                                                "short-pass",
                                                "운영자",
                                                "OPERATOR")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_PASSWORD_FORMAT));
        assertThatThrownBy(
                        () ->
                                service.create(
                                        900L,
                                        new CreateAdminAccountRequest(
                                                "operator@limit.local",
                                                null,
                                                "운영자",
                                                "OPERATOR")))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(accounts, logs);
    }

    @Test
    void createRejectsMissingOrBlankName() {
        assertThatThrownBy(
                        () ->
                                service.create(
                                        900L,
                                        new CreateAdminAccountRequest(
                                                "operator@limit.local",
                                                "sufficiently-long-password",
                                                "   ",
                                                "SUPER_ADMIN")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(
                        () ->
                                service.create(
                                        900L,
                                        new CreateAdminAccountRequest(
                                                "operator@limit.local",
                                                "sufficiently-long-password",
                                                null,
                                                "SUPER_ADMIN")))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(accounts, logs);
    }

    @Test
    void createRejectsDuplicatedEmailIgnoringCase() {
        when(accounts.findByEmailIgnoreCase("operator@limit.local"))
                .thenReturn(Optional.of(account(11L, "operator@limit.local", "OPERATOR")));

        assertThatThrownBy(
                        () ->
                                service.create(
                                        900L,
                                        new CreateAdminAccountRequest(
                                                "OPERATOR@LIMIT.LOCAL",
                                                "sufficiently-long-password",
                                                "운영자",
                                                "OPERATOR")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ADMIN_EMAIL_DUPLICATED));
        verify(accounts, never()).save(any(AdminAccount.class));
        verifyNoInteractions(logs);
    }

    @Test
    void updateThrowsWhenAdminAccountDoesNotExist() {
        when(accounts.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.update(
                                        900L,
                                        99L,
                                        new UpdateAdminAccountAccessRequest(
                                                "OPERATOR", "ACTIVE")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ADMIN_NOT_FOUND));
        verifyNoInteractions(logs);
    }

    @Test
    void updateRejectsRequestWithNeitherRoleNorStatus() {
        when(accounts.findById(11L))
                .thenReturn(Optional.of(account(11L, "operator@limit.local", "OPERATOR")));

        assertThatThrownBy(
                        () ->
                                service.update(
                                        900L,
                                        11L,
                                        new UpdateAdminAccountAccessRequest("   ", null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verifyNoInteractions(logs);
    }

    @Test
    void updateRejectsUnknownRole() {
        when(accounts.findById(11L))
                .thenReturn(Optional.of(account(11L, "operator@limit.local", "OPERATOR")));

        assertThatThrownBy(
                        () ->
                                service.update(
                                        900L,
                                        11L,
                                        new UpdateAdminAccountAccessRequest("INSPECTOR", null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_ADMIN_ROLE));
        verifyNoInteractions(logs);
    }

    @Test
    void updateRejectsUnknownStatus() {
        when(accounts.findById(11L))
                .thenReturn(Optional.of(account(11L, "operator@limit.local", "OPERATOR")));

        assertThatThrownBy(
                        () ->
                                service.update(
                                        900L,
                                        11L,
                                        new UpdateAdminAccountAccessRequest(null, "DELETED")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_ADMIN_STATUS));
        verifyNoInteractions(logs);
    }

    @Test
    void updateSuspendsOperatorWithoutConsultingSuperAdminGuard() {
        AdminAccount operator = account(11L, "operator@limit.local", "OPERATOR");
        when(accounts.findById(11L)).thenReturn(Optional.of(operator));

        AdminAccountResponse response =
                service.update(
                        900L, 11L, new UpdateAdminAccountAccessRequest(null, "SUSPENDED"));

        assertThat(response.status()).isEqualTo("SUSPENDED");
        assertThat(operator.getRole()).isEqualTo("OPERATOR");
        verify(accounts, never()).findActiveSuperAdminsForUpdate();
        verify(accounts).flush();
        verify(logs).save(any(AdminActionLog.class));
    }

    @Test
    void updateSkipsGuardWhenSuperAdminIsAlreadySuspended() {
        AdminAccount suspended = account(11L, "root@limit.local", "SUPER_ADMIN");
        suspended.updateAccess(null, "SUSPENDED");
        when(accounts.findById(11L)).thenReturn(Optional.of(suspended));

        AdminAccountResponse response =
                service.update(
                        900L, 11L, new UpdateAdminAccountAccessRequest("OPERATOR", null));

        assertThat(response.role()).isEqualTo("OPERATOR");
        assertThat(response.status()).isEqualTo("SUSPENDED");
        verify(accounts, never()).findActiveSuperAdminsForUpdate();
    }

    @Test
    void updateBlocksSuspendingTheOnlyActiveSuperAdmin() {
        AdminAccount root = account(11L, "root@limit.local", "SUPER_ADMIN");
        when(accounts.findById(11L)).thenReturn(Optional.of(root));
        when(accounts.findActiveSuperAdminsForUpdate()).thenReturn(List.of(root));

        assertThatThrownBy(
                        () ->
                                service.update(
                                        900L,
                                        11L,
                                        new UpdateAdminAccountAccessRequest(null, "SUSPENDED")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LAST_SUPER_ADMIN));
        assertThat(root.getStatus()).isEqualTo("ACTIVE");
        verify(accounts, never()).flush();
        verifyNoInteractions(logs);
    }

    @Test
    void updateKeepsSuperAdminRoleWhenOnlyStatusStaysActive() {
        AdminAccount root = account(11L, "root@limit.local", "SUPER_ADMIN");
        when(accounts.findById(11L)).thenReturn(Optional.of(root));

        AdminAccountResponse response =
                service.update(
                        900L, 11L, new UpdateAdminAccountAccessRequest("SUPER_ADMIN", "ACTIVE"));

        assertThat(response.role()).isEqualTo("SUPER_ADMIN");
        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(accounts, never()).findActiveSuperAdminsForUpdate();
        verify(accounts).flush();
    }

    private AdminAccount account(Long id, String email, String role) {
        AdminAccount account =
                AdminAccount.createInitial(email, "encoded-password", "이름", role);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }
}
