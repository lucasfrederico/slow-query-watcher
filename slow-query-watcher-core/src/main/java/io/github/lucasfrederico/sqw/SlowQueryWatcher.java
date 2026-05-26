package io.github.lucasfrederico.sqw;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central dispatcher: receives raw timings from the proxy chain and, when the
 * threshold is exceeded and the SQL isn't excluded, builds a {@link SlowQueryEvent}
 * and fans it out to every registered {@link SlowQueryListener}.
 *
 * <p>Listeners run on the calling thread. A listener that throws is logged at
 * {@code WARN} and does not prevent other listeners from running.
 */
public final class SlowQueryWatcher {

    private static final Logger log = LoggerFactory.getLogger(SlowQueryWatcher.class);

    private final WatcherConfig config;
    private final List<SlowQueryListener> listeners;
    private final TransactionIdProvider transactionIdProvider;

    public SlowQueryWatcher(WatcherConfig config, List<SlowQueryListener> listeners) {
        this(config, listeners, TransactionIdProvider.NONE);
    }

    public SlowQueryWatcher(
            WatcherConfig config,
            List<SlowQueryListener> listeners,
            TransactionIdProvider transactionIdProvider) {
        this.config = Objects.requireNonNull(config, "config");
        this.listeners = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(listeners, "listeners")));
        this.transactionIdProvider = Objects.requireNonNull(transactionIdProvider, "transactionIdProvider");
    }

    public WatcherConfig config() {
        return config;
    }

    /**
     * Called by the proxy chain after each timed JDBC execution.
     *
     * @param sql            the SQL string executed (may be {@code null} for batch executions
     *                       whose statements weren't all bound to the same SQL)
     * @param parameters     prepared-statement parameter values for this execution, or
     *                       {@code null} if not a {@code PreparedStatement}
     * @param durationNanos  measured wall-time in nanoseconds
     */
    public void onExecution(String sql, Object[] parameters, long durationNanos) {
        long durationMs = durationNanos / 1_000_000L;
        if (durationMs < config.thresholdMs()) return;
        if (config.isExcluded(sql)) return;
        if (listeners.isEmpty()) return;

        Object[] reportedParameters = switch (config.parameterCapture()) {
            case NONE -> null;
            case REDACTED -> redact(parameters);
            case FULL -> parameters;
        };

        String[] stack = config.captureStackTrace() ? StackTraceCapture.capture(config.stackTraceMaxFrames()) : null;

        String txId;
        try {
            txId = transactionIdProvider.currentTransactionId();
        } catch (RuntimeException e) {
            log.warn("transactionIdProvider failed; reporting null", e);
            txId = null;
        }

        SlowQueryEvent event = new SlowQueryEvent(
                sql,
                reportedParameters,
                durationMs,
                stack,
                txId,
                Instant.now(),
                config.dataSourceName());

        for (SlowQueryListener l : listeners) {
            try {
                l.onSlowQuery(event);
            } catch (RuntimeException e) {
                log.warn("SlowQueryListener {} threw; continuing", l.getClass().getName(), e);
            }
        }
    }

    private static Object[] redact(Object[] parameters) {
        if (parameters == null) return null;
        Object[] out = new Object[parameters.length];
        for (int i = 0; i < out.length; i++) out[i] = "?";
        return out;
    }
}
