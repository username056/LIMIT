package com.c203.limit;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationIntegrationTests {

    @Container
    static final MySQLContainer MYSQL =
            new MySQLContainer(DockerImageName.parse("mysql:8.4"))
                    .withDatabaseName("limit_legacy")
                    .withUsername("limit")
                    .withPassword("test-only-password");

    @Container
    static final MySQLContainer PRODUCTION_HISTORY_MYSQL =
            new MySQLContainer(DockerImageName.parse("mysql:8.4"))
                    .withDatabaseName("limit_production_history")
                    .withUsername("limit")
                    .withPassword("test-only-password");

    @BeforeAll
    static void migrateLegacySchema() throws SQLException {
        try (var connection =
                        DriverManager.getConnection(
                                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                var statement = connection.createStatement()) {
            statement.execute(
                    """
                    CREATE TABLE user_account (
                        user_id BIGINT NOT NULL AUTO_INCREMENT,
                        email VARCHAR(255) NOT NULL,
                        password VARCHAR(255) NULL,
                        nickname VARCHAR(50) NOT NULL,
                        phone VARCHAR(20) NULL,
                        member_type VARCHAR(20) NOT NULL,
                        status VARCHAR(30) NOT NULL,
                        marketing_opt_in BIT(1) NOT NULL DEFAULT b'0',
                        created_at DATETIME(6) NOT NULL,
                        updated_at DATETIME(6) NOT NULL,
                        PRIMARY KEY (user_id),
                        UNIQUE KEY uk_user_account_email (email),
                        UNIQUE KEY uk_user_account_nickname (nickname)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            statement.execute(
                    """
                    CREATE TABLE admin_account (
                        admin_id BIGINT NOT NULL AUTO_INCREMENT,
                        email VARCHAR(255) NOT NULL,
                        password VARCHAR(255) NOT NULL,
                        name VARCHAR(255) NOT NULL,
                        role VARCHAR(255) NOT NULL,
                        status VARCHAR(255) NOT NULL,
                        created_at DATETIME(6) NOT NULL,
                        PRIMARY KEY (admin_id),
                        UNIQUE KEY uk_admin_account_email (email)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            statement.execute("CREATE TABLE member_role (member_role_id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE user_sanction (sanction_id BIGINT PRIMARY KEY)");
            statement.execute(
                    """
                    INSERT INTO user_account (
                        email, password, nickname, member_type, status,
                        marketing_opt_in, created_at, updated_at
                    ) VALUES (
                        'legacy@example.com', 'encoded', 'legacy-user', 'SELLER', 'ACTIVE',
                        b'0', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                    )
                    """);
        }

        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .cleanDisabled(true)
                .load()
                .migrate();
    }

    @BeforeAll
    static void migrateProductionHistory() throws SQLException {
        migrateTo(PRODUCTION_HISTORY_MYSQL, "20260723");
        migrateTo(PRODUCTION_HISTORY_MYSQL, "20260727");

        assertThat(
                        singleLong(
                                PRODUCTION_HISTORY_MYSQL,
                                "SELECT COUNT(*) FROM flyway_schema_history "
                                        + "WHERE version > '20260723' AND version < '20260727'"))
                .isZero();
        assertThat(
                        singleString(
                                PRODUCTION_HISTORY_MYSQL,
                                "SELECT version FROM flyway_schema_history "
                                        + "WHERE success = 1 AND version IS NOT NULL "
                                        + "ORDER BY installed_rank DESC LIMIT 1"))
                .isEqualTo("20260727");

        migrateTo(PRODUCTION_HISTORY_MYSQL, null);
    }

    @Test
    void migratesLegacySchemaWithoutDeletingLegacyRoleData() throws SQLException {
        assertThat(columnExists("admin_account", "updated_at")).isTrue();
        assertThat(columnExists("admin_account", "last_login_at")).isTrue();
        assertThat(columnExists("admin_account", "password_changed_at")).isTrue();
        assertThat(columnExists("user_account", "email_verified_at")).isTrue();
        assertThat(columnExists("user_account", "last_login_at")).isTrue();
        assertThat(columnExists("user_account", "password_changed_at")).isTrue();
        assertThat(tableExists("member_terms_agreement")).isTrue();
        assertThat(tableExists("member_sanction")).isTrue();
        assertThat(tableExists("admin_action_log")).isTrue();
        assertThat(tableExists("chat_room")).isTrue();
        assertThat(tableExists("ocr_result")).isTrue();
        assertThat(tableExists("payment")).isTrue();
        assertThat(tableExists("rtc_session_checklist_result")).isTrue();
        assertThat(tableExists("listing")).isTrue();
        assertThat(tableExists("checklist_template_item")).isTrue();
        assertThat(columnExists("listing", "color")).isTrue();
        assertThat(columnExists("listing", "storage_gb")).isTrue();
        assertThat(columnExists("listing", "trade_region")).isTrue();
        assertThat(columnExists("category", "manufacturer_id")).isTrue();
        assertThat(columnExists("category", "supported_storage_gb")).isTrue();
        assertThat(indexExists("listing", "idx_listing_public_feed")).isTrue();
        assertThat(indexExists("listing", "idx_listing_seller_feed")).isTrue();
        assertThat(indexExists("listing", "idx_listing_seller_all_feed")).isTrue();
        assertThat(indexExists("wishlist", "idx_wishlist_user_created")).isTrue();
        assertThat(indexExists("listing_image", "idx_listing_image_thumbnail")).isTrue();
        assertThat(columnExists("rtc_session", "verification_memo")).isTrue();
        assertThat(indexExists("rtc_session", "uk_rtc_session_appointment")).isTrue();
        assertThat(tableExists("member_role")).isTrue();
        assertThat(tableExists("user_sanction")).isTrue();
        assertThat(singleString("SELECT member_type FROM user_account WHERE email = 'legacy@example.com'"))
                .isEqualTo("SELLER");
        assertThat(singleLong("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"))
                .isGreaterThanOrEqualTo(7L);
        assertThat(singleString(
                        "SELECT version FROM flyway_schema_history "
                                + "WHERE success = 1 AND version IS NOT NULL "
                                + "ORDER BY installed_rank DESC LIMIT 1"))
                .isEqualTo("20260731");
    }

    @Test
    void migratesPendingChangesAfterExistingProductionProductMigration() throws SQLException {
        assertThat(
                        singleString(
                                PRODUCTION_HISTORY_MYSQL,
                                "SELECT version FROM flyway_schema_history "
                                        + "WHERE success = 1 AND version IS NOT NULL "
                                        + "ORDER BY installed_rank DESC LIMIT 1"))
                .isEqualTo("20260731");
        assertThat(
                        singleLong(
                                PRODUCTION_HISTORY_MYSQL,
                                "SELECT COUNT(*) FROM flyway_schema_history "
                                        + "WHERE success = 1 "
                                        + "AND version IN ('20260728', '20260729', '20260730', '20260731')"))
                .isEqualTo(4L);
    }

    private static void migrateTo(MySQLContainer container, String target) {
        var configuration =
                Flyway.configure()
                        .dataSource(
                                container.getJdbcUrl(),
                                container.getUsername(),
                                container.getPassword())
                        .locations("classpath:db/migration")
                        .cleanDisabled(true);
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private static boolean tableExists(String tableName) throws SQLException {
        return singleLong(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_name = '"
                                + tableName
                                + "'")
                == 1L;
    }

    private static boolean columnExists(String tableName, String columnName) throws SQLException {
        return singleLong(
                        "SELECT COUNT(*) FROM information_schema.columns "
                                + "WHERE table_schema = DATABASE() AND table_name = '"
                                + tableName
                                + "' AND column_name = '"
                                + columnName
                                + "'")
                == 1L;
    }

    private static boolean indexExists(String tableName, String indexName) throws SQLException {
        return singleLong(
                        "SELECT COUNT(*) FROM information_schema.statistics "
                                + "WHERE table_schema = DATABASE() AND table_name = '"
                                + tableName
                                + "' AND index_name = '"
                                + indexName
                                + "'")
                > 0L;
    }

    private static long singleLong(String sql) throws SQLException {
        return singleLong(MYSQL, sql);
    }

    private static long singleLong(MySQLContainer container, String sql) throws SQLException {
        try (var connection =
                        DriverManager.getConnection(
                                container.getJdbcUrl(),
                                container.getUsername(),
                                container.getPassword());
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static String singleString(String sql) throws SQLException {
        return singleString(MYSQL, sql);
    }

    private static String singleString(MySQLContainer container, String sql) throws SQLException {
        try (var connection =
                        DriverManager.getConnection(
                                container.getJdbcUrl(),
                                container.getUsername(),
                                container.getPassword());
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}
