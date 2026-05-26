package io.github.lucasfrederico.sqw.spring;

import io.github.lucasfrederico.sqw.SlowQueryEvent;
import io.github.lucasfrederico.sqw.SlowQueryListener;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * Default listener: writes one structured SLF4J line per slow query. Structured
 * fields go through SLF4J key/value pairs (rendered as MDC by logback's JSON
 * encoder, or as appended pairs by the SLF4J text formatter).
 */
public class Slf4jSlowQueryListener implements SlowQueryListener {

    private static final Logger log = LoggerFactory.getLogger("slow-query-watcher");

    private final Level level;

    public Slf4jSlowQueryListener(Level level) {
        this.level = level;
    }

    @Override
    public void onSlowQuery(SlowQueryEvent event) {
        LoggingEventBuilder e = log.atLevel(level)
                .addKeyValue("sql", event.sql())
                .addKeyValue("duration_ms", event.durationMs())
                .addKeyValue("data_source", event.dataSourceName())
                .addKeyValue("timestamp", event.timestamp());

        if (event.parameters() != null) {
            e = e.addKeyValue("parameters", Arrays.toString(event.parameters()));
        }
        if (event.transactionId() != null) {
            e = e.addKeyValue("tx_id", event.transactionId());
        }
        if (event.stackTrace() != null && event.stackTrace().length > 0) {
            e = e.addKeyValue("caller", event.stackTrace()[0]);
        }

        String traceId = MDC.get("traceId");
        if (traceId != null) e = e.addKeyValue("trace_id", traceId);

        e.log("Slow query: {} ms — {}", event.durationMs(), event.sql());

        if (log.isDebugEnabled() && event.stackTrace() != null) {
            for (String frame : event.stackTrace()) {
                log.debug("  at {}", frame);
            }
        }
    }
}
