package io.github.lucasfrederico.sqw.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.lucasfrederico.sqw.proxy.DataSourceProxy;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
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
        classes = MultiDataSourceIT.TestApp.class,
        properties = "slow-query-watcher.threshold=100ms")
class MultiDataSourceIT {

    @Container
    static PostgreSQLContainer<?> pgPrimary = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container
    static PostgreSQLContainer<?> pgSecondary = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("app.primary.url", pgPrimary::getJdbcUrl);
        r.add("app.primary.user", pgPrimary::getUsername);
        r.add("app.primary.pwd", pgPrimary::getPassword);
        r.add("app.secondary.url", pgSecondary::getJdbcUrl);
        r.add("app.secondary.user", pgSecondary::getUsername);
        r.add("app.secondary.pwd", pgSecondary::getPassword);
    }

    @Autowired @Qualifier("primaryDataSource") DataSource primary;
    @Autowired @Qualifier("secondaryDataSource") DataSource secondary;
    @Autowired @Qualifier("primaryJdbc") JdbcTemplate primaryJdbc;
    @Autowired @Qualifier("secondaryJdbc") JdbcTemplate secondaryJdbc;
    @Autowired RecordingListener recording;

    @BeforeEach
    void reset() {
        recording.clear();
    }

    @Test
    void bothDataSourcesWrappedAndDistinguishable() {
        assertThat(primary).isInstanceOf(DataSourceProxy.class);
        assertThat(secondary).isInstanceOf(DataSourceProxy.class);
        assertThat(primary).isNotSameAs(secondary);

        primaryJdbc.queryForObject("SELECT count(*)::int FROM pg_sleep(0.3)", Integer.class);
        secondaryJdbc.queryForObject("SELECT count(*)::int FROM pg_sleep(0.3)", Integer.class);

        assertThat(recording.events()).hasSize(2);
        assertThat(recording.events().stream().map(e -> e.dataSourceName()).toList())
                .containsExactlyInAnyOrder("primaryDataSource", "secondaryDataSource");
    }

    public static void main(String[] args) {
        SpringApplication.run(TestApp.class, args);
    }

    @SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
    static class TestApp {

        @Bean
        DataSource primaryDataSource(org.springframework.core.env.Environment env) {
            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl(env.getRequiredProperty("app.primary.url"));
            cfg.setUsername(env.getRequiredProperty("app.primary.user"));
            cfg.setPassword(env.getRequiredProperty("app.primary.pwd"));
            cfg.setPoolName("primary");
            return new HikariDataSource(cfg);
        }

        @Bean
        DataSource secondaryDataSource(org.springframework.core.env.Environment env) {
            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl(env.getRequiredProperty("app.secondary.url"));
            cfg.setUsername(env.getRequiredProperty("app.secondary.user"));
            cfg.setPassword(env.getRequiredProperty("app.secondary.pwd"));
            cfg.setPoolName("secondary");
            return new HikariDataSource(cfg);
        }

        @Bean
        JdbcTemplate primaryJdbc(@Qualifier("primaryDataSource") DataSource ds) {
            return new JdbcTemplate(ds);
        }

        @Bean
        JdbcTemplate secondaryJdbc(@Qualifier("secondaryDataSource") DataSource ds) {
            return new JdbcTemplate(ds);
        }

        @Bean
        RecordingListener recordingListener() {
            return new RecordingListener();
        }
    }
}
