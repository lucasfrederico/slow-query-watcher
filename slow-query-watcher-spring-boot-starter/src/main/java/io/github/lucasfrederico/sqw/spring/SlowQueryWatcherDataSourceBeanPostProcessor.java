package io.github.lucasfrederico.sqw.spring;

import io.github.lucasfrederico.sqw.SlowQueryListener;
import io.github.lucasfrederico.sqw.SlowQueryWatcher;
import io.github.lucasfrederico.sqw.TransactionIdProvider;
import io.github.lucasfrederico.sqw.WatcherConfig;
import io.github.lucasfrederico.sqw.proxy.DataSourceProxy;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Replaces every {@link DataSource} bean with a {@link DataSourceProxy} during
 * {@code postProcessAfterInitialization} so subsequent autowiring receives the
 * proxied instance.
 */
public class SlowQueryWatcherDataSourceBeanPostProcessor implements BeanPostProcessor {

    private final SlowQueryProperties properties;
    private final List<SlowQueryListener> listeners;
    private final TransactionIdProvider transactionIdProvider;

    public SlowQueryWatcherDataSourceBeanPostProcessor(
            SlowQueryProperties properties,
            List<SlowQueryListener> listeners,
            TransactionIdProvider transactionIdProvider) {
        this.properties = properties;
        this.listeners = listeners;
        this.transactionIdProvider = transactionIdProvider;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!(bean instanceof DataSource ds)) return bean;
        if (bean instanceof DataSourceProxy) return bean;

        WatcherConfig cfg = SlowQueryWatcherAutoConfiguration.toCoreConfig(properties, beanName);
        SlowQueryWatcher watcher = new SlowQueryWatcher(cfg, listeners, transactionIdProvider);
        return new DataSourceProxy(ds, watcher);
    }
}
