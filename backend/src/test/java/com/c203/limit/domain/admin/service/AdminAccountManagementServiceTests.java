package com.c203.limit.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.c203.limit.domain.admin.dto.request.UpdateAdminAccountAccessRequest;
import com.c203.limit.domain.admin.entity.AdminAccount;
import com.c203.limit.domain.admin.repository.AdminAccountRepository;
import com.c203.limit.domain.admin.repository.AdminActionLogRepository;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
}
