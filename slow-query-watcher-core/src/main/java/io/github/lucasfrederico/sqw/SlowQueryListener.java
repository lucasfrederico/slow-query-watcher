package io.github.lucasfrederico.sqw;

/**
 * Callback invoked whenever a query takes longer than the configured threshold.
 *
 * <p>Implementations should be cheap and non-blocking. The listener runs on the
 * calling thread, after the JDBC call returns and before control flows back to
 * application code, so anything expensive here adds latency to every slow query.
 */
@FunctionalInterface
public interface SlowQueryListener {
    void onSlowQuery(SlowQueryEvent event);
}
