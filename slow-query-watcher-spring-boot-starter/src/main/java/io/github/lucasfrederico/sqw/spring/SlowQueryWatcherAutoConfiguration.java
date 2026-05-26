package io.github.lucasfrederico.sqw.spring;

import io.github.lucasfrederico.sqw.SlowQueryListener;
import io.github.lucasfrederico.sqw.SlowQueryWatcher;
import io.github.lucasfrederico.sqw.TransactionIdProvider;
import io.github.lucasfrederico.sqw.WatcherConfig;
import io.github.lucasfrederico.sqw.proxy.DataSourceProxy;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.event.Level;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Wires the slow-query-watcher into a Spring Boot application.
 *
 * <p>When {@code slow-query-watcher.enabled} is {@code true} (the default), every
 * {@link DataSource} bean is wrapped with a {@link DataSourceProxy} via a
 * {@link BeanPostProcessor}. The wrapping happens before Hibernate, MyBatis, or
 * Spring's {@code JdbcTemplate} ever sees the DataSource, so all JDBC traffic
 * flows through the proxy chain.
 */
@AutoConfiguration(before = DataSourceAutoConfiguration.class)
@ConditionalOnClass(DataSource.class)
@ConditionalOnProperty(prefix = "slow-query-watcher", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SlowQueryProperties.class)
public class SlowQueryWatcherAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TransactionIdProvider slowQueryTransactionIdProvider() {
        try {
            // org.springframework.transaction is optional; gracefully degrade if missing.
            Class.forName("org.springframework.transaction.support.TransactionSynchronizationManager");
            return new SpringTransactionIdProvider();
        } catch (ClassNotFoundException e) {
            return TransactionIdProvider.NONE;
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public SlowQueryListener slowQueryDefaultListener(SlowQueryProperties props) {
        return new Slf4jSlowQueryListener(toSlf4jLevel(props.getLogLevel()));
    }

    @Bean
    public SlowQueryWatcherDataSourceBeanPostProcessor slowQueryDataSourceBeanPostProcessor(
            SlowQueryProperties props,
            List<SlowQueryListener> listeners,
            TransactionIdProvider txIdProvider) {
        return new SlowQueryWatcherDataSourceBeanPostProcessor(props, listeners, txIdProvider);
    }

    private static Level toSlf4jLevel(SlowQueryProperties.LogLevel level) {
        return switch (level) {
            case TRACE -> Level.TRACE;
            case DEBUG -> Level.DEBUG;
            case INFO -> Level.INFO;
            case WARN -> Level.WARN;
            case ERROR -> Level.ERROR;
        };
    }

    static WatcherConfig toCoreConfig(SlowQueryProperties props, String dataSourceName) {
        return WatcherConfig.builder()
                .thresholdMs(props.getThreshold().toMillis())
                .captureStackTrace(props.isCaptureStackTrace())
                .stackTraceMaxFrames(props.getStackTraceMaxFrames())
                .parameterCapture(props.getCaptureParameters())
                .excludePatterns(props.getExcludePatterns())
                .dataSourceName(dataSourceName)
                .build();
    }
}
