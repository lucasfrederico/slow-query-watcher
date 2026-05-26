package io.github.lucasfrederico.sqw.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lucasfrederico.sqw.proxy.DataSourceProxy;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
        classes = PostgresSlowQueryIT.TestApp.class,
        properties = {
                "slow-query-watcher.threshold=100ms",
                "slow-query-watcher.exclude-patterns[0]=^SELECT 1$",
                "spring.jpa.open-in-view=false"
        })
class PostgresSlowQueryIT {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
        r.add("spring.datasource.driver-class-name", pg::getDriverClassName);
    }

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired RecordingListener recording;

    @BeforeEach
    void reset() {
        recording.clear();
    }

    @Test
    void dataSourceIsWrapped() {
        assertThat(dataSource).isInstanceOf(DataSourceProxy.class);
    }

    @Test
    void slowQueryEmitsEvent() {
        jdbc.queryForObject("SELECT count(*)::int FROM pg_sleep(0.4)", Integer.class);

        assertThat(recording.events()).hasSize(1);
        var ev = recording.events().get(0);
        assertThat(ev.sql()).contains("pg_sleep");
        assertThat(ev.durationMs()).isGreaterThanOrEqualTo(100L);
        assertThat(ev.timestamp()).isNotNull();
        assertThat(ev.dataSourceName()).isEqualTo("dataSource");
    }

    @Test
    void fastQueryStaysSilent() {
        jdbc.queryForObject("SELECT 42", Integer.class);

        assertThat(recording.events()).isEmpty();
    }

    @Test
    void excludePatternFiltersBeforeListener() {
        jdbc.queryForObject("SELECT 1", Integer.class);

        assertThat(recording.events()).isEmpty();
    }

    @Test
    void preparedStatementCapturesParameters() {
        jdbc.update("CREATE TABLE IF NOT EXISTS u (id int primary key, email text)");
        jdbc.update("INSERT INTO u(id, email) VALUES (?, ?)", 1, "lucas@example.com");
        // Force a slow read
        jdbc.queryForObject(
                "SELECT id FROM u, pg_sleep(0.3) AS s WHERE id = ?", Integer.class, 1);

        var slow = recording.events().stream()
                .filter(e -> e.sql() != null && e.sql().contains("pg_sleep"))
                .findFirst()
                .orElseThrow();
        assertThat(slow.parameters()).containsExactly(1);
    }

    @SpringBootApplication
    static class TestApp {
        @Bean
        RecordingListener recordingListener() {
            return new RecordingListener();
        }
    }
}
