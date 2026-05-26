package io.github.lucasfrederico.sqw;

/**
 * How prepared-statement parameters are exposed on {@link SlowQueryEvent#parameters()}.
 */
public enum ParameterCapture {
    /** Capture parameter values as-is. Use with care: values may contain PII. */
    FULL,
    /** Capture parameter positions but replace every value with the literal string {@code "?"}. */
    REDACTED,
    /** Do not capture parameters at all — {@code event.parameters()} will be {@code null}. */
    NONE
}
