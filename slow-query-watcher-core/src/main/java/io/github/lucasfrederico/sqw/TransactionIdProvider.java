package io.github.lucasfrederico.sqw;

/**
 * Resolves a transaction identifier for the calling thread, or {@code null} if no
 * transaction is active. Implementations are framework-specific (Spring's
 * {@code TransactionSynchronizationManager} is wired in the Spring Boot starter
 * module).
 */
@FunctionalInterface
public interface TransactionIdProvider {

    String currentTransactionId();

    /** No-op provider used by plain JDBC integrations. */
    TransactionIdProvider NONE = () -> null;
}
