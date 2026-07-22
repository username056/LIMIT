package com.c203.limit.domain.admin.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(AdminBootstrapProperties.class)
@ConditionalOnProperty(name = "limit.bootstrap.admin.enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {
    private final AdminBootstrapService service;
    private final AdminBootstrapProperties properties;

    public AdminBootstrapRunner(
            AdminBootstrapService service, AdminBootstrapProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        service.ensureInitialAdmin(properties);
    }
}
