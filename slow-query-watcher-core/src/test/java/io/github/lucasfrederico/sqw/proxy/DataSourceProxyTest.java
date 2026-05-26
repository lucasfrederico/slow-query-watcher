package io.github.lucasfrederico.sqw.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.lucasfrederico.sqw.ParameterCapture;
import io.github.lucasfrederico.sqw.SlowQueryEvent;
import io.github.lucasfrederico.sqw.SlowQueryWatcher;
import io.github.lucasfrederico.sqw.WatcherConfig;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class DataSourceProxyTest {

    @Test
    void preparedStatementExecutionTimedAndForwarded() throws Exception {
        DataSource real = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);

        when(real.getConnection()).thenReturn(conn);
        when(conn.prepareStatement("SELECT 1")).thenReturn(ps);
        when(ps.executeQuery()).thenAnswer(inv -> { Thread.sleep(50); return null; });

        List<SlowQueryEvent> sink = new ArrayList<>();
        WatcherConfig cfg = WatcherConfig.builder().thresholdMs(10).captureStackTrace(false).build();
        DataSourceProxy proxy = new DataSourceProxy(real, new SlowQueryWatcher(cfg, List.of(sink::add)));

        try (Connection wrapped = proxy.getConnection();
             PreparedStatement wrappedPs = wrapped.prepareStatement("SELECT 1")) {
            wrappedPs.setInt(1, 7);
            wrappedPs.executeQuery();
        }

        verify(ps).setInt(eq(1), eq(7));
        verify(ps).executeQuery();
        assertThat(sink).hasSize(1);
        assertThat(sink.get(0).sql()).isEqualTo("SELECT 1");
        assertThat(sink.get(0).parameters()).containsExactly(7);
        assertThat(sink.get(0).durationMs()).isGreaterThanOrEqualTo(10L);
    }

    @Test
    void fastQueriesAreSilent() throws Exception {
        DataSource real = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);

        when(real.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(null);

        List<SlowQueryEvent> sink = new ArrayList<>();
        WatcherConfig cfg = WatcherConfig.builder().thresholdMs(500).captureStackTrace(false).build();
        DataSourceProxy proxy = new DataSourceProxy(real, new SlowQueryWatcher(cfg, List.of(sink::add)));

        try (Connection wrapped = proxy.getConnection();
             PreparedStatement wrappedPs = wrapped.prepareStatement("SELECT 1")) {
            wrappedPs.executeQuery();
        }

        assertThat(sink).isEmpty();
    }

    @Test
    void plainStatementTakesSqlFromArg() throws Exception {
        DataSource real = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        Statement st = mock(Statement.class);

        when(real.getConnection()).thenReturn(conn);
        when(conn.createStatement()).thenReturn(st);
        when(st.executeQuery(anyString())).thenAnswer(inv -> { Thread.sleep(30); return null; });

        List<SlowQueryEvent> sink = new ArrayList<>();
        WatcherConfig cfg = WatcherConfig.builder().thresholdMs(10).captureStackTrace(false).build();
        DataSourceProxy proxy = new DataSourceProxy(real, new SlowQueryWatcher(cfg, List.of(sink::add)));

        try (Connection wrapped = proxy.getConnection();
             Statement wrappedSt = wrapped.createStatement()) {
            wrappedSt.executeQuery("SELECT * FROM x");
        }

        assertThat(sink).hasSize(1);
        assertThat(sink.get(0).sql()).isEqualTo("SELECT * FROM x");
        assertThat(sink.get(0).parameters()).isNull();
    }

    @Test
    void redactedParametersExposeOnlyPositions() throws Exception {
        DataSource real = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);

        when(real.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeUpdate()).thenAnswer(inv -> { Thread.sleep(20); return 1; });

        List<SlowQueryEvent> sink = new ArrayList<>();
        WatcherConfig cfg = WatcherConfig.builder()
                .thresholdMs(10)
                .captureStackTrace(false)
                .parameterCapture(ParameterCapture.REDACTED)
                .build();
        DataSourceProxy proxy = new DataSourceProxy(real, new SlowQueryWatcher(cfg, List.of(sink::add)));

        try (Connection wrapped = proxy.getConnection();
             PreparedStatement wrappedPs = wrapped.prepareStatement("UPDATE u SET email = ? WHERE id = ?")) {
            wrappedPs.setString(1, "lucas@example.com");
            wrappedPs.setLong(2, 42L);
            wrappedPs.executeUpdate();
        }

        assertThat(sink.get(0).parameters()).containsExactly("?", "?");
    }

    @Test
    void unwrapAlsoReturnsItself() throws Exception {
        DataSource real = mock(DataSource.class);
        DataSourceProxy proxy = new DataSourceProxy(real, new SlowQueryWatcher(WatcherConfig.defaults(), List.of()));

        assertThat(proxy.unwrap(DataSource.class)).isSameAs(proxy);
        assertThat(proxy.isWrapperFor(DataSource.class)).isTrue();
    }

    @Test
    void thrownExceptionStillTriggersTiming() throws Exception {
        DataSource real = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);

        when(real.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenAnswer(inv -> { Thread.sleep(30); throw new java.sql.SQLException("kaboom"); });

        List<SlowQueryEvent> sink = new ArrayList<>();
        WatcherConfig cfg = WatcherConfig.builder().thresholdMs(10).captureStackTrace(false).build();
        DataSourceProxy proxy = new DataSourceProxy(real, new SlowQueryWatcher(cfg, List.of(sink::add)));

        try (Connection wrapped = proxy.getConnection();
             PreparedStatement wrappedPs = wrapped.prepareStatement("SELECT 1")) {
            try {
                wrappedPs.executeQuery();
            } catch (java.sql.SQLException ignored) {
                // expected
            }
        }

        verify(ps, atLeastOnce()).executeQuery();
        assertThat(sink).hasSize(1);
    }

    @Test
    @SuppressWarnings("unused")
    void connectionMethodsForwardThrough() throws Exception {
        DataSource real = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        when(real.getConnection()).thenReturn(conn);

        DataSourceProxy proxy = new DataSourceProxy(real, new SlowQueryWatcher(WatcherConfig.defaults(), List.of()));

        try (Connection wrapped = proxy.getConnection()) {
            wrapped.setAutoCommit(false);
            wrapped.commit();
            wrapped.rollback();
        }
        verify(conn).setAutoCommit(false);
        verify(conn).commit();
        verify(conn).rollback();
    }
}
