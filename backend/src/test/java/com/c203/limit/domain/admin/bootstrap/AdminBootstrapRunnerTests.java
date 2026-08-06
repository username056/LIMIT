package com.c203.limit.domain.admin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTests {

    @Mock AdminBootstrapService service;

    @Test
    void runDelegatesConfiguredPropertiesToBootstrapService() {
        AdminBootstrapProperties properties = properties("admin@limit.local", "SUPER_ADMIN");

        new AdminBootstrapRunner(service, properties).run(new DefaultApplicationArguments());

        var captor = ArgumentCaptor.forClass(AdminBootstrapProperties.class);
        verify(service).ensureInitialAdmin(captor.capture());
        assertThat(captor.getValue()).isSameAs(properties);
        assertThat(captor.getValue().getEmail()).isEqualTo("admin@limit.local");
        assertThat(captor.getValue().getPassword()).isEqualTo("ChangeMe123!");
        assertThat(captor.getValue().getName()).isEqualTo("초기 관리자");
        assertThat(captor.getValue().getRole()).isEqualTo("SUPER_ADMIN");
    }

    @Test
    void runSucceedsQuietlyWhenAdminAlreadyExists() {
        AdminBootstrapProperties properties = properties("admin@limit.local", "SUPER_ADMIN");
        when(service.ensureInitialAdmin(properties)).thenReturn(false);
        AdminBootstrapRunner runner = new AdminBootstrapRunner(service, properties);

        assertThatCode(() -> runner.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();

        verify(service).ensureInitialAdmin(properties);
    }

    @Test
    void runPropagatesInvalidConfigurationFailure() {
        AdminBootstrapProperties properties = properties(null, "SUPER_ADMIN");
        when(service.ensureInitialAdmin(properties))
                .thenThrow(
                        new IllegalStateException(
                                "INITIAL_ADMIN_EMAIL must be a valid email address"));
        AdminBootstrapRunner runner = new AdminBootstrapRunner(service, properties);
        var arguments = new DefaultApplicationArguments();

        assertThatThrownBy(() -> runner.run(arguments))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("INITIAL_ADMIN_EMAIL must be a valid email address");
    }

    @Test
    void runAcceptsNullApplicationArgumentsBecauseTheyAreUnused() {
        AdminBootstrapProperties properties = properties("admin@limit.local", "OPERATOR");

        new AdminBootstrapRunner(service, properties).run(null);

        verify(service).ensureInitialAdmin(properties);
    }

    private AdminBootstrapProperties properties(String email, String role) {
        AdminBootstrapProperties properties = new AdminBootstrapProperties();
        properties.setEmail(email);
        properties.setPassword("ChangeMe123!");
        properties.setName("초기 관리자");
        properties.setRole(role);
        return properties;
    }
}
