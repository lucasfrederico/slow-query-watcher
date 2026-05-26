package io.github.lucasfrederico.sqw;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable configuration for a {@link SlowQueryWatcher}.
 *
 * <p>Built via {@link #builder()} or via {@link #defaults()} for the out-of-the-box
 * profile (100ms threshold, full parameter capture, stack trace enabled).
 */
public final class WatcherConfig {

    private final long thresholdMs;
    private final ParameterCapture parameterCapture;
    private final boolean captureStackTrace;
    private final int stackTraceMaxFrames;
    private final List<Pattern> excludePatterns;
    private final String dataSourceName;

    private WatcherConfig(Builder b) {
        this.thresholdMs = b.thresholdMs;
        this.parameterCapture = b.parameterCapture;
        this.captureStackTrace = b.captureStackTrace;
        this.stackTraceMaxFrames = b.stackTraceMaxFrames;
        this.excludePatterns = Collections.unmodifiableList(new ArrayList<>(b.excludePatterns));
        this.dataSourceName = b.dataSourceName;
    }

    public static WatcherConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public long thresholdMs() { return thresholdMs; }
    public ParameterCapture parameterCapture() { return parameterCapture; }
    public boolean captureStackTrace() { return captureStackTrace; }
    public int stackTraceMaxFrames() { return stackTraceMaxFrames; }
    public List<Pattern> excludePatterns() { return excludePatterns; }
    public String dataSourceName() { return dataSourceName; }

    /** Returns {@code true} when {@code sql} matches any configured exclude pattern. */
    public boolean isExcluded(String sql) {
        if (sql == null || excludePatterns.isEmpty()) return false;
        for (Pattern p : excludePatterns) {
            if (p.matcher(sql).find()) return true;
        }
        return false;
    }

    public static final class Builder {
        private long thresholdMs = 100;
        private ParameterCapture parameterCapture = ParameterCapture.FULL;
        private boolean captureStackTrace = true;
        private int stackTraceMaxFrames = 32;
        private List<Pattern> excludePatterns = new ArrayList<>();
        private String dataSourceName = "dataSource";

        public Builder thresholdMs(long thresholdMs) {
            if (thresholdMs < 0) throw new IllegalArgumentException("thresholdMs must be >= 0");
            this.thresholdMs = thresholdMs;
            return this;
        }

        public Builder threshold(Duration d) {
            return thresholdMs(Objects.requireNonNull(d, "threshold").toMillis());
        }

        public Builder parameterCapture(ParameterCapture p) {
            this.parameterCapture = Objects.requireNonNull(p, "parameterCapture");
            return this;
        }

        public Builder captureStackTrace(boolean v) {
            this.captureStackTrace = v;
            return this;
        }

        public Builder stackTraceMaxFrames(int n) {
            if (n < 1) throw new IllegalArgumentException("stackTraceMaxFrames must be >= 1");
            this.stackTraceMaxFrames = n;
            return this;
        }

        public Builder excludePatterns(List<String> regexes) {
            Objects.requireNonNull(regexes, "regexes");
            this.excludePatterns = new ArrayList<>(regexes.size());
            for (String r : regexes) this.excludePatterns.add(Pattern.compile(r));
            return this;
        }

        public Builder addExcludePattern(String regex) {
            this.excludePatterns.add(Pattern.compile(regex));
            return this;
        }

        public Builder dataSourceName(String name) {
            this.dataSourceName = Objects.requireNonNullElse(name, "dataSource");
            return this;
        }

        public WatcherConfig build() {
            return new WatcherConfig(this);
        }
    }
}
