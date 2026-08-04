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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class TransportLifecycleMigrationIntegrationTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("ersync_lifecycle_migration")
            .withUsername("ersync_local")
            .withPassword("ersync_local_password");

    @Test
    void v8PreservesV7RequestAndAddsConstrainedLifecycleHistory() throws Exception {
        migrateToVersionSeven();
        insertVersionSevenRequest();

        Flyway.configure()
                .dataSource(jdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet request = statement.executeQuery("""
                    SELECT status, cancellation_reason, handoff_requested_at, completed_at
                    FROM transport_requests
                    WHERE public_id = '00000000-0000-0000-0000-000000000003'
                    """)) {
                assertThat(request.next()).isTrue();
                assertThat(request.getString("status")).isEqualTo("SEARCHING");
                assertThat(request.getString("cancellation_reason")).isNull();
                assertThat(request.getTimestamp("handoff_requested_at")).isNull();
                assertThat(request.getTimestamp("completed_at")).isNull();
            }

            statement.executeUpdate("""
                    UPDATE transport_requests
                    SET status = 'CANCELLED',
                        cancellation_reason = 'SCENE_RESOLVED',
                        cancelled_by_account_id = 1,
                        cancelled_at = '2026-08-04 12:00:00.000000'
                    WHERE id = 1
                    """);
            statement.executeUpdate("""
                    INSERT INTO transport_lifecycle_commands (
                        public_id, transport_request_id, command_type,
                        actor_account_id, actor_organization_id, destination_offer_id,
                        cancellation_reason, cancellation_detail, idempotency_key,
                        request_fingerprint, resulting_request_status, occurred_at
                    ) VALUES (
                        '00000000-0000-0000-0000-000000000004', 1, 'CANCEL',
                        1, 1, NULL, 'SCENE_RESOLVED', NULL, 'migration-cancel-key',
                        UNHEX(REPEAT('01', 32)), 'CANCELLED', '2026-08-04 12:00:00.000000'
                    )
                    """);

            try (ResultSet command = statement.executeQuery("""
                    SELECT command_type, cancellation_reason, resulting_request_status
                    FROM transport_lifecycle_commands
                    WHERE transport_request_id = 1
                    """)) {
                assertThat(command.next()).isTrue();
                assertThat(command.getString("command_type")).isEqualTo("CANCEL");
                assertThat(command.getString("cancellation_reason")).isEqualTo("SCENE_RESOLVED");
                assertThat(command.getString("resulting_request_status")).isEqualTo("CANCELLED");
            }

            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO transport_lifecycle_commands (
                        public_id, transport_request_id, command_type,
                        actor_account_id, actor_organization_id, destination_offer_id,
                        cancellation_reason, cancellation_detail, idempotency_key,
                        request_fingerprint, resulting_request_status, occurred_at
                    ) VALUES (
                        '00000000-0000-0000-0000-000000000005', 1, 'CANCEL',
                        1, 1, NULL, 'OTHER', NULL, 'invalid-other-key',
                        UNHEX(REPEAT('02', 32)), 'CANCELLED', '2026-08-04 12:01:00.000000'
                    )
                    """)).isInstanceOf(java.sql.SQLException.class);
        }
    }

    private void migrateToVersionSeven() {
        Flyway.configure()
                .dataSource(jdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("7"))
                .load()
                .migrate();
    }

    private void insertVersionSevenRequest() throws Exception {
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
                        1, '00000000-0000-0000-0000-000000000002', 1, 'legacylifecycle',
                        'encoded-password', 'PARAMEDIC', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO transport_requests (
                        id, public_id, owner_account_id, organization_id, status,
                        current_destination_offer_id, callback_contact, assessment_protocol_version,
                        origin_latitude, origin_longitude, origin_source, client_idempotency_key,
                        request_fingerprint, server_received_at, created_at, updated_at, version
                    ) VALUES (
                        1, '00000000-0000-0000-0000-000000000003', 1, 1, 'SEARCHING',
                        NULL, '010-0000-0001', 'ERSYNC_MVP_1.0',
                        37.5821000, 127.0105000, 'GPS', 'legacy-request-key',
                        UNHEX(REPEAT('00', 32)), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0
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
