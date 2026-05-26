package io.github.lucasfrederico.sqw.proxy;

import io.github.lucasfrederico.sqw.SlowQueryWatcher;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * {@link DataSource} wrapper that returns proxied connections. Every connection
 * handed out by this DataSource has its statements/prepared-statements timed.
 *
 * <p>The wrapped DataSource is unchanged — Hikari, Tomcat JDBC, c3p0, or any
 * vendor-specific implementation can sit behind this proxy.
 */
public final class DataSourceProxy implements DataSource {

    private final DataSource delegate;
    private final SlowQueryWatcher watcher;

    public DataSourceProxy(DataSource delegate, SlowQueryWatcher watcher) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.watcher = Objects.requireNonNull(watcher, "watcher");
    }

    public DataSource delegate() {
        return delegate;
    }

    public SlowQueryWatcher watcher() {
        return watcher;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrap(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrap(delegate.getConnection(username, password));
    }

    private Connection wrap(Connection real) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new ConnectionInvocationHandler(real, watcher));
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) return (T) this;
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        if (iface.isInstance(this)) return true;
        return delegate.isWrapperFor(iface);
    }
}
