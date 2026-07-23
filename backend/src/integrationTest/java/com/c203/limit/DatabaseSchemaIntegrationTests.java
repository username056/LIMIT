package com.c203.limit;

import static org.assertj.core.api.Assertions.assertThat;

import com.c203.limit.domain.admin.entity.AdminAccount;
import com.c203.limit.domain.admin.repository.AdminAccountRepository;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        properties =
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration,"
                        + "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                        + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration")
class DatabaseSchemaIntegrationTests {

    @Container
    static final MySQLContainer MYSQL =
            new MySQLContainer(DockerImageName.parse("mysql:8.4"))
                    .withDatabaseName("limit")
                    .withUsername("limit")
                    .withPassword("test-only-password");

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired MemberRepository memberRepository;
    @Autowired AdminAccountRepository adminAccountRepository;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    void appliesMigrationsAndValidatesJpaSchema() {
        assertThat(tableExists("user_account")).isTrue();
        assertThat(tableExists("social_account")).isTrue();
        assertThat(tableExists("member_terms_agreement")).isTrue();
        assertThat(tableExists("admin_account")).isTrue();
        assertThat(tableExists("member_sanction")).isTrue();
        assertThat(tableExists("admin_action_log")).isTrue();
    }

    @Test
    void auditsMemberAndAdminTimestamps() {
        Member member =
                memberRepository.saveAndFlush(
                        Member.createLocal(
                                "audit-member@example.com",
                                "encoded-password",
                                "audit-member",
                                null));
        assertThat(member.getCreatedAt()).isNotNull();
        assertThat(member.getUpdatedAt()).isEqualTo(member.getCreatedAt());

        var memberCreatedAt = member.getCreatedAt();
        member.updateProfile("audit-member-updated", null);
        memberRepository.flush();
        assertThat(member.getUpdatedAt()).isAfterOrEqualTo(memberCreatedAt);

        AdminAccount admin =
                adminAccountRepository.saveAndFlush(
                        AdminAccount.createInitial(
                                "audit-admin@example.com",
                                "encoded-password",
                                "audit-admin",
                                "SUPER_ADMIN"));
        assertThat(admin.getCreatedAt()).isNotNull();
        assertThat(admin.getUpdatedAt()).isEqualTo(admin.getCreatedAt());

        var adminCreatedAt = admin.getCreatedAt();
        admin.updateAccess("OPERATOR", null);
        adminAccountRepository.flush();
        assertThat(admin.getUpdatedAt()).isAfterOrEqualTo(adminCreatedAt);
    }

    private boolean tableExists(String tableName) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_name = ?",
                        Integer.class,
                        tableName);
        return count != null && count == 1;
    }
}
