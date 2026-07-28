package com.c203.limit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
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

    @Container
    static final MySQLContainer FAILED_SELLER_MIGRATION_MYSQL =
            new MySQLContainer(DockerImageName.parse("mysql:8.4"))
                    .withDatabaseName("limit_failed_seller_migration")
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
                    CREATE TABLE seller (
                        seller_id BIGINT NOT NULL AUTO_INCREMENT,
                        seller_name VARCHAR(100) NOT NULL,
                        seller_category VARCHAR(30) NOT NULL,
                        country VARCHAR(50) NOT NULL,
                        status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                        product_limit INT NOT NULL DEFAULT 0,
                        sales_amount_limit BIGINT NOT NULL DEFAULT 0,
                        approved_at DATETIME(6) NULL,
                        created_at DATETIME(6) NOT NULL,
                        PRIMARY KEY (seller_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            statement.execute(
                    """
                    INSERT INTO seller (
                        seller_name, seller_category, country, status, created_at
                    ) VALUES (
                        'legacy-shop', 'INDIVIDUAL', 'KR', 'ACTIVE', CURRENT_TIMESTAMP(6)
                    )
                    """);
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
        assertThat(singleLong("SELECT COUNT(*) FROM category WHERE parent_id IS NULL"))
                .isGreaterThanOrEqualTo(4L);
        assertThat(singleLong("SELECT COUNT(*) FROM category WHERE model_code IS NOT NULL"))
                .isGreaterThanOrEqualTo(7L);
        assertThat(
                        singleLong(
                                "SELECT COUNT(*) FROM checklist_template WHERE status = 'PUBLISHED'"))
                .isGreaterThanOrEqualTo(7L);
        assertThat(singleLong("SELECT COUNT(*) FROM checklist_template_item"))
                .isGreaterThanOrEqualTo(32L);
        assertThat(singleLong("SELECT COUNT(*) FROM account_removal_guide"))
                .isGreaterThanOrEqualTo(7L);
        assertThat(tableExists("member_role")).isTrue();
        assertThat(tableExists("user_sanction")).isTrue();
        assertThat(tableExists("seller")).isTrue();
        assertThat(columnExists("seller", "user_id")).isTrue();
        assertThat(columnExists("seller", "seller_type")).isTrue();
        assertThat(columnExists("seller", "settlement_account_last4")).isTrue();
        assertThat(indexExists("seller", "uk_seller_user")).isTrue();
        assertThat(singleString("SELECT seller_type FROM seller WHERE seller_name = 'legacy-shop'"))
                .isEqualTo("INDIVIDUAL");
        assertThat(
                        singleString(
                                "SELECT status FROM seller WHERE user_id = "
                                        + "(SELECT user_id FROM user_account "
                                        + "WHERE email = 'legacy@example.com')"))
                .isEqualTo("ACTIVE");
        assertThat(singleString("SELECT member_type FROM user_account WHERE email = 'legacy@example.com'"))
                .isEqualTo("SELLER");
        assertThat(singleLong("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"))
                .isGreaterThanOrEqualTo(7L);
        assertThat(
                        singleLong(
                                "SELECT COUNT(*) FROM flyway_schema_history "
                                        + "WHERE success = 1 AND version = '20260801'"))
                .isEqualTo(1L);
        assertThat(
                        singleLong(
                                "SELECT COUNT(*) FROM flyway_schema_history "
                                        + "WHERE success = 1 AND version = '20260802'"))
                .isEqualTo(1L);
    }

    @Test
    void migratesPendingChangesAfterExistingProductionProductMigration() throws SQLException {
        assertThat(
                        singleLong(
                                PRODUCTION_HISTORY_MYSQL,
                                "SELECT COUNT(*) FROM flyway_schema_history "
                                        + "WHERE success = 1 AND version = '20260801'"))
                .isEqualTo(1L);
        assertThat(
                        singleLong(
                                PRODUCTION_HISTORY_MYSQL,
                                "SELECT COUNT(*) FROM flyway_schema_history "
                                        + "WHERE success = 1 "
                                        + "AND version IN ('20260728', '20260729', '20260730', '20260731', '20260801', '20260802')"))
                .isEqualTo(6L);
    }

    @Test
    void repairsFailedSellerMigrationAfterPreparingProductionSchema()
            throws SQLException, IOException {
        createFailedSellerMigrationSchema();

        Flyway.configure()
                .dataSource(
                        FAILED_SELLER_MIGRATION_MYSQL.getJdbcUrl(),
                        FAILED_SELLER_MIGRATION_MYSQL.getUsername(),
                        FAILED_SELLER_MIGRATION_MYSQL.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .target("20260801")
                .cleanDisabled(true)
                .load()
                .migrate();

        var flyway =
                Flyway.configure()
                        .dataSource(
                                FAILED_SELLER_MIGRATION_MYSQL.getJdbcUrl(),
                                FAILED_SELLER_MIGRATION_MYSQL.getUsername(),
                                FAILED_SELLER_MIGRATION_MYSQL.getPassword())
                        .locations("classpath:db/migration")
                        .cleanDisabled(true)
                        .load();

        assertThatThrownBy(flyway::migrate).isInstanceOf(FlywayException.class);
        assertThat(
                        singleLong(
                                FAILED_SELLER_MIGRATION_MYSQL,
                                "SELECT COUNT(*) FROM flyway_schema_history "
                                        + "WHERE version = '20260802' AND success = 0"))
                .isEqualTo(1L);

        execute(
                FAILED_SELLER_MIGRATION_MYSQL,
                classpathSql(
                        "db/maintenance/V20260802__prepare_failed_seller_migration.sql"));
        flyway.repair();
        flyway.migrate();

        assertThat(
                        singleLong(
                                FAILED_SELLER_MIGRATION_MYSQL,
                                "SELECT COUNT(*) FROM flyway_schema_history "
                                        + "WHERE version = '20260802' AND success = 1"))
                .isEqualTo(1L);
        assertThat(
                        singleLong(
                                FAILED_SELLER_MIGRATION_MYSQL,
                                "SELECT COUNT(*) FROM flyway_schema_history "
                                        + "WHERE version = '20260802' AND success = 0"))
                .isZero();
        assertThat(
                        singleLong(
                                FAILED_SELLER_MIGRATION_MYSQL,
                                "SELECT COUNT(*) FROM seller"))
                .isEqualTo(1L);
        assertThat(
                        singleLong(
                                FAILED_SELLER_MIGRATION_MYSQL,
                                "SELECT COUNT(*) FROM user_account account "
                                        + "LEFT JOIN seller profile ON profile.user_id = account.user_id "
                                        + "WHERE account.member_type = 'SELLER' "
                                        + "AND profile.user_id IS NULL"))
                .isZero();
        assertThat(
                        singleLong(
                                FAILED_SELLER_MIGRATION_MYSQL,
                                "SELECT COUNT(*) FROM seller "
                                        + "WHERE approved_at IS NULL "
                                        + "AND product_limit = 0 "
                                        + "AND sales_amount_limit = 0"))
                .isEqualTo(1L);
    }

    private static void createFailedSellerMigrationSchema() throws SQLException {
        try (var connection =
                        DriverManager.getConnection(
                                FAILED_SELLER_MIGRATION_MYSQL.getJdbcUrl(),
                                FAILED_SELLER_MIGRATION_MYSQL.getUsername(),
                                FAILED_SELLER_MIGRATION_MYSQL.getPassword());
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
                    CREATE TABLE seller (
                        seller_id BIGINT NOT NULL AUTO_INCREMENT,
                        approved_at DATETIME(6) NOT NULL,
                        business_name VARCHAR(255) NULL,
                        country_code VARCHAR(2) NOT NULL,
                        product_limit INT NOT NULL,
                        sales_amount_limit DECIMAL(38, 2) NOT NULL,
                        status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                        seller_type VARCHAR(20) NOT NULL,
                        user_id BIGINT NOT NULL,
                        settlement_bank_name VARCHAR(100) NOT NULL,
                        settlement_account_holder VARCHAR(100) NOT NULL,
                        settlement_account_last4 VARCHAR(4) NOT NULL,
                        created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                        updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                        PRIMARY KEY (seller_id),
                        UNIQUE KEY uk_seller_user_id (user_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            statement.execute(
                    """
                    INSERT INTO user_account (
                        email, password, nickname, member_type, status,
                        marketing_opt_in, created_at, updated_at
                    ) VALUES (
                        'seller-recovery@example.com', 'encoded', 'seller-recovery',
                        'SELLER', 'ACTIVE', b'0', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                    )
                    """);
        }
    }

    private static String classpathSql(String path) throws IOException {
        try (var input = FlywayMigrationIntegrationTests.class.getClassLoader()
                .getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("classpath SQL not found: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void execute(MySQLContainer container, String sql) throws SQLException {
        try (var connection =
                        DriverManager.getConnection(
                                container.getJdbcUrl(),
                                container.getUsername(),
                                container.getPassword());
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
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
