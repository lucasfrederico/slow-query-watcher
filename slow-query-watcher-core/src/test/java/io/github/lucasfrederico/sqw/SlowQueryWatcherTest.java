package io.github.lucasfrederico.sqw;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SlowQueryWatcherTest {

    private static final long MS = 1_000_000L;

    @Test
    void eventDispatchedAboveThreshold() {
        List<SlowQueryEvent> sink = new ArrayList<>();
        SlowQueryWatcher w = new SlowQueryWatcher(
                WatcherConfig.builder().thresholdMs(50).captureStackTrace(false).build(),
                List.of(sink::add));

        w.onExecution("SELECT 1", new Object[]{1}, 60 * MS);

        assertThat(sink).hasSize(1);
        assertThat(sink.get(0).durationMs()).isEqualTo(60L);
        assertThat(sink.get(0).sql()).isEqualTo("SELECT 1");
        assertThat(sink.get(0).parameters()).containsExactly(1);
    }

    @Test
    void belowThresholdIsSilent() {
        List<SlowQueryEvent> sink = new ArrayList<>();
        SlowQueryWatcher w = new SlowQueryWatcher(
                WatcherConfig.builder().thresholdMs(100).captureStackTrace(false).build(),
                List.of(sink::add));

        w.onExecution("SELECT 1", null, 50 * MS);

        assertThat(sink).isEmpty();
    }

    @Test
    void excludePatternsPrevail() {
        List<SlowQueryEvent> sink = new ArrayList<>();
        SlowQueryWatcher w = new SlowQueryWatcher(
                WatcherConfig.builder()
                        .thresholdMs(1)
                        .captureStackTrace(false)
                        .addExcludePattern("^SELECT 1$")
                        .build(),
                List.of(sink::add));

        w.onExecution("SELECT 1", null, 500 * MS);

        assertThat(sink).isEmpty();
    }

    @Test
    void redactedModeReplacesValues() {
        List<SlowQueryEvent> sink = new ArrayList<>();
        SlowQueryWatcher w = new SlowQueryWatcher(
                WatcherConfig.builder()
                        .thresholdMs(1)
                        .captureStackTrace(false)
                        .parameterCapture(ParameterCapture.REDACTED)
                        .build(),
                List.of(sink::add));

        w.onExecution("UPDATE u SET name = ?", new Object[]{"lucas"}, 5 * MS);

        assertThat(sink.get(0).parameters()).containsExactly("?");
    }

    @Test
    void noneModeNullsParameters() {
        List<SlowQueryEvent> sink = new ArrayList<>();
        SlowQueryWatcher w = new SlowQueryWatcher(
                WatcherConfig.builder()
                        .thresholdMs(1)
                        .captureStackTrace(false)
                        .parameterCapture(ParameterCapture.NONE)
                        .build(),
                List.of(sink::add));

        w.onExecution("UPDATE u SET name = ?", new Object[]{"lucas"}, 5 * MS);

        assertThat(sink.get(0).parameters()).isNull();
    }

    @Test
    void stackTraceCapturedWhenEnabled() {
        List<SlowQueryEvent> sink = new ArrayList<>();
        SlowQueryWatcher w = new SlowQueryWatcher(
                WatcherConfig.builder().thresholdMs(1).captureStackTrace(true).build(),
                List.of(sink::add));

        w.onExecution("SELECT 1", null, 5 * MS);

        String[] frames = sink.get(0).stackTrace();
        assertThat(frames).isNotNull().isNotEmpty();
        // Internal proxy/watcher frames must not leak into the reported trace.
        assertThat(frames)
                .noneMatch(f -> f.startsWith("io.github.lucasfrederico.sqw.SlowQueryWatcher.")
                                || f.startsWith("io.github.lucasfrederico.sqw.proxy.")
                                || f.startsWith("io.github.lucasfrederico.sqw.StackTraceCapture"));
    }

    @Test
    void listenerExceptionDoesNotBlockOthers() {
        List<SlowQueryEvent> sink = new ArrayList<>();
        SlowQueryListener boom = e -> { throw new RuntimeException("boom"); };

        SlowQueryWatcher w = new SlowQueryWatcher(
                WatcherConfig.builder().thresholdMs(1).captureStackTrace(false).build(),
                List.of(boom, sink::add));

        w.onExecution("SELECT 1", null, 5 * MS);

        assertThat(sink).hasSize(1);
    }

    @Test
    void txIdProviderUsed() {
        List<SlowQueryEvent> sink = new ArrayList<>();
        TransactionIdProvider provider = () -> "tx-42";
        SlowQueryWatcher w = new SlowQueryWatcher(
                WatcherConfig.builder().thresholdMs(1).captureStackTrace(false).build(),
                List.of(sink::add),
                provider);

        w.onExecution("SELECT 1", null, 5 * MS);

        assertThat(sink.get(0).transactionId()).isEqualTo("tx-42");
    }

    @Test
    void txIdProviderExceptionReportsNull() {
        List<SlowQueryEvent> sink = new ArrayList<>();
        TransactionIdProvider broken = () -> { throw new RuntimeException("bad"); };
        SlowQueryWatcher w = new SlowQueryWatcher(
                WatcherConfig.builder().thresholdMs(1).captureStackTrace(false).build(),
                List.of(sink::add),
                broken);

        w.onExecution("SELECT 1", null, 5 * MS);

        assertThat(sink.get(0).transactionId()).isNull();
    }
}
