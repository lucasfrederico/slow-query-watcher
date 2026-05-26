package io.github.lucasfrederico.sqw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class WatcherConfigTest {

    @Test
    void defaultsAreSensible() {
        WatcherConfig cfg = WatcherConfig.defaults();
        assertThat(cfg.thresholdMs()).isEqualTo(100L);
        assertThat(cfg.parameterCapture()).isEqualTo(ParameterCapture.FULL);
        assertThat(cfg.captureStackTrace()).isTrue();
        assertThat(cfg.dataSourceName()).isEqualTo("dataSource");
        assertThat(cfg.excludePatterns()).isEmpty();
    }

    @Test
    void durationConvertsToMillis() {
        WatcherConfig cfg = WatcherConfig.builder().threshold(Duration.ofMillis(250)).build();
        assertThat(cfg.thresholdMs()).isEqualTo(250L);
    }

    @Test
    void excludePatternsMatchAnywhere() {
        WatcherConfig cfg = WatcherConfig.builder()
                .excludePatterns(List.of("^SELECT 1$", "pg_sleep"))
                .build();
        assertThat(cfg.isExcluded("SELECT 1")).isTrue();
        assertThat(cfg.isExcluded("SELECT pg_sleep(1)")).isTrue();
        assertThat(cfg.isExcluded("SELECT * FROM users")).isFalse();
        assertThat(cfg.isExcluded(null)).isFalse();
    }

    @Test
    void emptyExcludeListIsFastPath() {
        WatcherConfig cfg = WatcherConfig.defaults();
        assertThat(cfg.isExcluded("any sql")).isFalse();
    }

    @Test
    void negativeThresholdRejected() {
        assertThatThrownBy(() -> WatcherConfig.builder().thresholdMs(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidStackFramesRejected() {
        assertThatThrownBy(() -> WatcherConfig.builder().stackTraceMaxFrames(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
