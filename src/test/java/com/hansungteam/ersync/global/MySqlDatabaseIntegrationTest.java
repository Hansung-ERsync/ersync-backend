package com.hansungteam.ersync.global;

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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MySqlDatabaseIntegrationTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("ersync")
            .withUsername("ersync_local")
            .withPassword("ersync_local_password");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MySqlDatabaseIntegrationTest::jdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Test
    void flywayAndJpaValidateRunOnMySql84() {
        String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);

        assertThat(version).startsWith("8.4");
    }

    private static String jdbcUrl() {
        return MYSQL.getJdbcUrl()
                + "?connectionTimeZone=UTC"
                + "&forceConnectionTimeZoneToSession=true";
    }
}
