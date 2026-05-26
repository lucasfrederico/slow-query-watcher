package io.github.lucasfrederico.sqw;

import java.time.Instant;

/**
 * A captured slow query and the context around it.
 *
 * <p>{@code parameters} is {@code null} when {@link ParameterCapture#NONE} is configured,
 * and {@link ParameterCapture#REDACTED} replaces every value with the literal string
 * {@code "?"}. {@code stackTrace} is {@code null} when stack capture is disabled.
 * {@code transactionId} is {@code null} when no transaction is active or no provider
 * is wired (e.g. plain JDBC without Spring).
 */
public record SlowQueryEvent(
        String sql,
        Object[] parameters,
        long durationMs,
        String[] stackTrace,
        String transactionId,
        Instant timestamp,
        String dataSourceName) {}
