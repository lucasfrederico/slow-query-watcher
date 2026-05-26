package io.github.lucasfrederico.sqw.proxy;

import io.github.lucasfrederico.sqw.SlowQueryWatcher;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

/**
 * Intercepts {@code prepareStatement*}, {@code prepareCall*}, and {@code createStatement*}
 * on a real {@link Connection}, wrapping the returned statements with a
 * {@link StatementInvocationHandler}. Everything else delegates straight through.
 */
final class ConnectionInvocationHandler implements InvocationHandler {

    private static final Class<?>[] PS_IFACES = {PreparedStatement.class};
    private static final Class<?>[] CS_IFACES = {CallableStatement.class};
    private static final Class<?>[] ST_IFACES = {Statement.class};

    private final Connection delegate;
    private final SlowQueryWatcher watcher;

    ConnectionInvocationHandler(Connection delegate, SlowQueryWatcher watcher) {
        this.delegate = delegate;
        this.watcher = watcher;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();

        Object result;
        try {
            result = method.invoke(delegate, args);
        } catch (InvocationTargetException ite) {
            throw ite.getCause();
        }

        if (result == null) return null;

        if (name.equals("prepareStatement") && args != null && args.length >= 1 && args[0] instanceof String sql) {
            return wrapStatement((PreparedStatement) result, sql, PS_IFACES);
        }
        if (name.equals("prepareCall") && args != null && args.length >= 1 && args[0] instanceof String sql) {
            return wrapStatement((CallableStatement) result, sql, CS_IFACES);
        }
        if (name.equals("createStatement")) {
            return wrapStatement((Statement) result, null, ST_IFACES);
        }
        if (name.equals("unwrap")) {
            // Honor unwrap() — return the real connection or its unwrap result.
            return result;
        }
        return result;
    }

    private Object wrapStatement(Statement real, String boundSql, Class<?>[] ifaces) {
        return Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                ifaces,
                new StatementInvocationHandler(real, watcher, boundSql));
    }
}
