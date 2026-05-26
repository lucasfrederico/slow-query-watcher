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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
        classes = MySQLSlowQueryIT.TestApp.class,
        properties = "slow-query-watcher.threshold=100ms")
class MySQLSlowQueryIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
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
        jdbc.queryForObject("SELECT SLEEP(0.4)", Integer.class);

        assertThat(recording.events()).hasSize(1);
        var ev = recording.events().get(0);
        assertThat(ev.sql()).contains("SLEEP");
        assertThat(ev.durationMs()).isGreaterThanOrEqualTo(100L);
    }

    @Test
    void fastQueryStaysSilent() {
        jdbc.queryForObject("SELECT 42", Integer.class);
        assertThat(recording.events()).isEmpty();
    }

    @SpringBootApplication
    static class TestApp {
        @Bean
        RecordingListener recordingListener() {
            return new RecordingListener();
        }
    }
}
