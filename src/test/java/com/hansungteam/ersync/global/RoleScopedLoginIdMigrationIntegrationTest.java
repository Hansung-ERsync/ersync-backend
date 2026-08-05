package com.hansungteam.ersync.global;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class RoleScopedLoginIdMigrationIntegrationTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("ersync_role_scoped_login_migration")
            .withUsername("ersync_local")
            .withPassword("ersync_local_password");

    @Test
    void v9KeepsExistingAccountsAndScopesLoginIdUniquenessByRole() throws Exception {
        migrateToVersionEight();
        insertVersionEightOrganizationsAndAccount();

        Flyway.configure()
                .dataSource(jdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            assertExistingParamedicWasPreserved(statement);

            statement.executeUpdate(accountInsert(
                    2,
                    "00000000-0000-0000-0000-000000000012",
                    2,
                    "sharedlogin",
                    "HOSPITAL_STAFF"
            ));

            assertThat(accountCount(statement, "sharedlogin")).isEqualTo(2);
            assertThat(roleCount(statement, "sharedlogin", "PARAMEDIC")).isEqualTo(1);
            assertThat(roleCount(statement, "sharedlogin", "HOSPITAL_STAFF")).isEqualTo(1);

            assertThatThrownBy(() -> statement.executeUpdate(accountInsert(
                    3,
                    "00000000-0000-0000-0000-000000000013",
                    3,
                    "sharedlogin",
                    "PARAMEDIC"
            ))).isInstanceOf(SQLException.class);

            try (ResultSet index = statement.executeQuery("""
                    SELECT COUNT(*) AS column_count
                    FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name = 'user_accounts'
                      AND index_name = 'uk_user_accounts_login_id_role'
                      AND non_unique = 0
                      AND column_name IN ('login_id', 'role')
                    """)) {
                assertThat(index.next()).isTrue();
                assertThat(index.getInt("column_count")).isEqualTo(2);
            }
        }
    }

    private void migrateToVersionEight() {
        Flyway.configure()
                .dataSource(jdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("8"))
                .load()
                .migrate();
    }

    private void insertVersionEightOrganizationsAndAccount() throws Exception {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO organizations (
                        id, public_id, name, type, status, created_at, updated_at
                    ) VALUES
                        (1, '00000000-0000-0000-0000-000000000001',
                         '기존 구급대', 'EMS_UNIT', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
                        (2, '00000000-0000-0000-0000-000000000002',
                         '기존 병원', 'HOSPITAL', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
                        (3, '00000000-0000-0000-0000-000000000003',
                         '다른 구급대', 'EMS_UNIT', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                    """);
            statement.executeUpdate(accountInsert(
                    1,
                    "00000000-0000-0000-0000-000000000011",
                    1,
                    "sharedlogin",
                    "PARAMEDIC"
            ));
        }
    }

    private void assertExistingParamedicWasPreserved(Statement statement) throws SQLException {
        try (ResultSet account = statement.executeQuery("""
                SELECT public_id, organization_id, role, status
                FROM user_accounts
                WHERE login_id = 'sharedlogin' AND role = 'PARAMEDIC'
                """)) {
            assertThat(account.next()).isTrue();
            assertThat(account.getString("public_id"))
                    .isEqualTo("00000000-0000-0000-0000-000000000011");
            assertThat(account.getLong("organization_id")).isEqualTo(1L);
            assertThat(account.getString("role")).isEqualTo("PARAMEDIC");
            assertThat(account.getString("status")).isEqualTo("ACTIVE");
            assertThat(account.next()).isFalse();
        }
    }

    private int accountCount(Statement statement, String loginId) throws SQLException {
        try (ResultSet count = statement.executeQuery("""
                SELECT COUNT(*) AS account_count
                FROM user_accounts
                WHERE login_id = '%s'
                """.formatted(loginId))) {
            assertThat(count.next()).isTrue();
            return count.getInt("account_count");
        }
    }

    private int roleCount(Statement statement, String loginId, String role) throws SQLException {
        try (ResultSet count = statement.executeQuery("""
                SELECT COUNT(*) AS account_count
                FROM user_accounts
                WHERE login_id = '%s' AND role = '%s'
                """.formatted(loginId, role))) {
            assertThat(count.next()).isTrue();
            return count.getInt("account_count");
        }
    }

    private String accountInsert(
            long id,
            String publicId,
            long organizationId,
            String loginId,
            String role
    ) {
        return """
                INSERT INTO user_accounts (
                    id, public_id, organization_id, login_id, password_hash, role, status,
                    created_at, updated_at
                ) VALUES (
                    %d, '%s', %d, '%s', 'encoded-password', '%s', 'ACTIVE',
                    UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
                )
                """.formatted(id, publicId, organizationId, loginId, role);
    }

    private Connection openConnection() throws Exception {
        return DriverManager.getConnection(jdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private String jdbcUrl() {
        return MYSQL.getJdbcUrl()
                + "?connectionTimeZone=UTC"
                + "&forceConnectionTimeZoneToSession=true";
    }
}
