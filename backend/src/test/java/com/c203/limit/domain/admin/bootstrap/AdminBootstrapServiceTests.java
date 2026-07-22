package com.c203.limit.domain.admin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.c203.limit.domain.admin.entity.AdminAccount;
import com.c203.limit.domain.admin.repository.AdminAccountRepository;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapServiceTests {
    @Mock AdminAccountRepository accounts;

    @Test
    void createsBcryptSuperAdminWhenEnabledConfigurationIsValid() {
        var encoder = new BCryptPasswordEncoder(4);
        var properties = properties(" Admin@Limit.Local ", "ChangeMe123!", "초기 관리자", "SUPER_ADMIN");
        var service = new AdminBootstrapService(accounts, encoder);
        assertThat(service.ensureInitialAdmin(properties)).isTrue();
        var captor = ArgumentCaptor.forClass(AdminAccount.class);
        verify(accounts).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("admin@limit.local");
        assertThat(captor.getValue().getRole()).isEqualTo("SUPER_ADMIN");
        assertThat(encoder.matches("ChangeMe123!", captor.getValue().getPassword())).isTrue();
    }

    @Test
    void doesNotOverwriteExistingAdmin() {
        var properties = properties("admin@limit.local", "ChangeMe123!", "초기 관리자", "SUPER_ADMIN");
        when(accounts.findByEmailIgnoreCase("admin@limit.local")).thenReturn(Optional.of(
                AdminAccount.createInitial("admin@limit.local", "encoded", "기존 관리자", "SUPER_ADMIN")));
        assertThat(new AdminBootstrapService(accounts, new BCryptPasswordEncoder(4))
                .ensureInitialAdmin(properties)).isFalse();
        verify(accounts, never()).save(any());
    }

    @Test
    void createsOperatorForBaseInit() {
        var encoder = new BCryptPasswordEncoder(4);
        var service = new AdminBootstrapService(accounts, encoder);

        assertThat(
                        service.ensureAccount(
                                "operator@limit.local",
                                "OperatorTest123!",
                                "테스트 운영자",
                                "OPERATOR"))
                .isTrue();

        var captor = ArgumentCaptor.forClass(AdminAccount.class);
        verify(accounts).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo("OPERATOR");
        assertThat(encoder.matches("OperatorTest123!", captor.getValue().getPassword())).isTrue();
    }

    @Test
    void rejectsShortInitialPassword() {
        var properties = properties("admin@limit.local", "short", "초기 관리자", "SUPER_ADMIN");
        assertThatThrownBy(() -> new AdminBootstrapService(accounts, new BCryptPasswordEncoder(4))
                .ensureInitialAdmin(properties)).isInstanceOf(IllegalStateException.class);
    }

    private AdminBootstrapProperties properties(String email, String password, String name, String role) {
        var properties = new AdminBootstrapProperties();
        properties.setEmail(email);
        properties.setPassword(password);
        properties.setName(name);
        properties.setRole(role);
        return properties;
    }
}
