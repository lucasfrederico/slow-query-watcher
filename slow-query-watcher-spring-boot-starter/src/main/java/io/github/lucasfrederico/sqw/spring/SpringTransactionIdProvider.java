package io.github.lucasfrederico.sqw.spring;

import io.github.lucasfrederico.sqw.TransactionIdProvider;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Returns Spring's current transaction name (the bean method annotated with
 * {@code @Transactional}, when active), or {@code null} when no transaction is in
 * scope. Kept in the starter module so {@code core} stays Spring-free.
 */
public class SpringTransactionIdProvider implements TransactionIdProvider {

    @Override
    public String currentTransactionId() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) return null;
        String name = TransactionSynchronizationManager.getCurrentTransactionName();
        return name != null ? name : "active";
    }
}
