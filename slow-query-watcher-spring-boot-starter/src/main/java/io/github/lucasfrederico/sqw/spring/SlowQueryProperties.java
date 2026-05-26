package io.github.lucasfrederico.sqw.spring;

import io.github.lucasfrederico.sqw.ParameterCapture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "slow-query-watcher")
public class SlowQueryProperties {

    /** Master switch. Set to {@code false} to skip wrapping any DataSource. */
    private boolean enabled = true;

    /** Queries faster than this are silent. */
    private Duration threshold = Duration.ofMillis(100);

    /** Log level for the built-in SLF4J listener. */
    private LogLevel logLevel = LogLevel.WARN;

    /** Capture a stack trace pointing to the caller of the slow query. */
    private boolean captureStackTrace = true;

    /** Max frames retained in the captured stack trace (after internal frames are stripped). */
    private int stackTraceMaxFrames = 32;

    /** {@code FULL} (default), {@code REDACTED}, or {@code NONE}. */
    private ParameterCapture captureParameters = ParameterCapture.FULL;

    /**
     * Regular expressions matched against the SQL text — if any matches, the query is
     * not reported even when slow. Default skips Hikari's connection-check query and
     * trivial driver round-trips.
     */
    private List<String> excludePatterns = new ArrayList<>(List.of(
            "^SELECT 1$",
            "^SELECT version\\(\\)$"
    ));

    public enum LogLevel { TRACE, DEBUG, INFO, WARN, ERROR }

    // getters/setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Duration getThreshold() { return threshold; }
    public void setThreshold(Duration threshold) { this.threshold = threshold; }

    public LogLevel getLogLevel() { return logLevel; }
    public void setLogLevel(LogLevel logLevel) { this.logLevel = logLevel; }

    public boolean isCaptureStackTrace() { return captureStackTrace; }
    public void setCaptureStackTrace(boolean captureStackTrace) { this.captureStackTrace = captureStackTrace; }

    public int getStackTraceMaxFrames() { return stackTraceMaxFrames; }
    public void setStackTraceMaxFrames(int stackTraceMaxFrames) { this.stackTraceMaxFrames = stackTraceMaxFrames; }

    public ParameterCapture getCaptureParameters() { return captureParameters; }
    public void setCaptureParameters(ParameterCapture captureParameters) { this.captureParameters = captureParameters; }

    public List<String> getExcludePatterns() { return excludePatterns; }
    public void setExcludePatterns(List<String> excludePatterns) { this.excludePatterns = excludePatterns; }
}
