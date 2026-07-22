package com.c203.limit.global.bootstrap;

import com.c203.limit.domain.admin.bootstrap.AdminBootstrapService;
import com.c203.limit.domain.auth.bootstrap.MemberBootstrapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(BaseInitDataProperties.class)
@ConditionalOnProperty(name = "limit.bootstrap.base-init.enabled", havingValue = "true")
public class BaseInitData implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BaseInitData.class);
    private static final String TEST_ADMIN_ROLE = "OPERATOR";

    private final MemberBootstrapService memberBootstrapService;
    private final AdminBootstrapService adminBootstrapService;
    private final BaseInitDataProperties properties;

    public BaseInitData(
            MemberBootstrapService memberBootstrapService,
            AdminBootstrapService adminBootstrapService,
            BaseInitDataProperties properties) {
        this.memberBootstrapService = memberBootstrapService;
        this.adminBootstrapService = adminBootstrapService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean memberCreated = false;
        boolean adminCreated = false;

        BaseInitDataProperties.MemberAccount member = properties.getMember();
        if (member.isEnabled()) {
            memberCreated =
                    memberBootstrapService.ensureVerifiedMember(
                            member.getEmail(),
                            member.getPassword(),
                            member.getNickname(),
                            member.getPhone());
        }

        BaseInitDataProperties.AdminAccount admin = properties.getAdmin();
        if (admin.isEnabled()) {
            adminCreated =
                    adminBootstrapService.ensureAccount(
                            admin.getEmail(),
                            admin.getPassword(),
                            admin.getName(),
                            TEST_ADMIN_ROLE);
        }

        log.info(
                "Base init accounts processed: memberCreated={}, operatorCreated={}",
                memberCreated,
                adminCreated);
    }
}
