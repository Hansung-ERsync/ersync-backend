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
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class SignupProfileMigrationIntegrationTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("ersync_profile_migration")
            .withUsername("ersync_local")
            .withPassword("ersync_local_password");

    @Test
    void v7BackfillsExistingParamedicNameAndCombinedConsentWithoutChangingOriginalFacts() throws Exception {
        migrateToVersionSix();
        insertVersionSixAccountData();

        Flyway.configure()
                .dataSource(jdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet profile = statement.executeQuery("""
                    SELECT display_name, contact
                    FROM paramedic_profiles
                    WHERE public_id = '00000000-0000-0000-0000-000000000003'
                    """)) {
                assertThat(profile.next()).isTrue();
                assertThat(profile.getString("display_name")).isEqualTo("legacymedic");
                assertThat(profile.getString("contact")).isEqualTo("010-0000-0001");
            }
            try (ResultSet consent = statement.executeQuery("""
                    SELECT consent_type, policy_version, consented_at
                    FROM contact_sharing_consents
                    WHERE public_id = '00000000-0000-0000-0000-000000000004'
                    """)) {
                assertThat(consent.next()).isTrue();
                assertThat(consent.getString("consent_type"))
                        .isEqualTo("CONTACT_COLLECTION_AND_PROVISION");
                assertThat(consent.getString("policy_version")).isEqualTo("CONTACT_SHARING_DEV_1.0");
                assertThat(consent.getTimestamp("consented_at").toInstant())
                        .isEqualTo("2026-08-03T09:00:00Z");
            }

            statement.executeUpdate("""
                    INSERT INTO contact_sharing_consents (
                        public_id, account_id, consent_type, policy_version, consented_at, created_at
                    ) VALUES
                        ('00000000-0000-0000-0000-000000000005', 1,
                         'CONTACT_COLLECTION_USE', 'SHARED_VERSION', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
                        ('00000000-0000-0000-0000-000000000006', 1,
                         'HOSPITAL_PROVISION', 'SHARED_VERSION', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                    """);
            try (ResultSet count = statement.executeQuery("""
                    SELECT COUNT(*) AS consent_count
                    FROM contact_sharing_consents
                    WHERE account_id = 1
                    """)) {
                assertThat(count.next()).isTrue();
                assertThat(count.getInt("consent_count")).isEqualTo(3);
            }
        }
    }

    private void migrateToVersionSix() {
        Flyway.configure()
                .dataSource(jdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("6"))
                .load()
                .migrate();
    }

    private void insertVersionSixAccountData() throws Exception {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO organizations (
                        id, public_id, name, type, status, created_at, updated_at
                    ) VALUES (
                        1, '00000000-0000-0000-0000-000000000001', '기존 구급대',
                        'EMS_UNIT', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO user_accounts (
                        id, public_id, organization_id, login_id, password_hash, role, status,
                        created_at, updated_at
                    ) VALUES (
                        1, '00000000-0000-0000-0000-000000000002', 1, 'legacymedic',
                        'encoded-password', 'PARAMEDIC', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO paramedic_profiles (
                        id, public_id, account_id, organization_id, contact, created_at, updated_at
                    ) VALUES (
                        1, '00000000-0000-0000-0000-000000000003', 1, 1,
                        '010-0000-0001', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO contact_sharing_consents (
                        id, public_id, account_id, policy_version, consented_at, created_at
                    ) VALUES (
                        1, '00000000-0000-0000-0000-000000000004', 1,
                        'CONTACT_SHARING_DEV_1.0', '2026-08-03 09:00:00.000000', UTC_TIMESTAMP(6)
                    )
                    """);
        }
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
