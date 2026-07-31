package com.hansungteam.ersync.global;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "ersync.auth.jwt-secret-base64=dGVzdC1qd3Qtc2VjcmV0LWtleS0zMi1ieXRlcy1mb3ItdGVzdHM=",
        "ersync.invitation.expiry-scheduler-enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
class MySqlDatabaseIntegrationTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("ersync")
            .withUsername("ersync_local")
            .withPassword("ersync_local_password");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MySqlDatabaseIntegrationTest::jdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Test
    void flywayAndJpaValidateRunOnMySql84() {
        String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
        Integer featureTableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN (
                      'organizations',
                      'user_accounts',
                      'invitation_codes',
                      'hospital_profiles',
                      'refresh_tokens',
                      'audit_events'
                  )
                """, Integer.class);

        assertThat(version).startsWith("8.4");
        assertThat(featureTableCount).isEqualTo(6);
    }

    @Test
    void readinessIsUpWithMigratedMySql84() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private static String jdbcUrl() {
        return MYSQL.getJdbcUrl()
                + "?connectionTimeZone=UTC"
                + "&forceConnectionTimeZoneToSession=true";
    }
}
