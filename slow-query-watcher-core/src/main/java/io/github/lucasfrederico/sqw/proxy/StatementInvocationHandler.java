package io.github.lucasfrederico.sqw.proxy;

import io.github.lucasfrederico.sqw.SlowQueryWatcher;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Statement;
import java.util.Map;
import java.util.TreeMap;

/**
 * Times {@code execute*} calls and tracks bound parameters for prepared statements.
 *
 * <p>For a plain {@link Statement}, {@code boundSql} is {@code null} and the SQL is
 * taken from the {@code String} arg of {@code execute(sql)} / {@code executeQuery(sql)}
 * / {@code executeUpdate(sql)}. For a prepared statement, {@code boundSql} carries the
 * SQL passed to {@code Connection.prepareStatement(sql)} and parameter values come from
 * {@code setXxx(int index, value)} calls.
 *
 * <p>Batch behavior is intentionally minimal in v0.1.0: {@code executeBatch} reports
 * the bound SQL (for {@code PreparedStatement}) or a {@code "<batch: N statements>"}
 * synthetic SQL (for plain {@code Statement}), with {@code parameters} set to {@code null}.
 */
final class StatementInvocationHandler implements InvocationHandler {

    private final Statement delegate;
    private final SlowQueryWatcher watcher;
    private final String boundSql;

    private final TreeMap<Integer, Object> currentParameters = new TreeMap<>();
    private String lastBatchSql;
    private int batchCount;

    StatementInvocationHandler(Statement delegate, SlowQueryWatcher watcher, String boundSql) {
        this.delegate = delegate;
        this.watcher = watcher;
        this.boundSql = boundSql;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();

        // Track parameter bindings for prepared statements (setXxx(int index, ...))
        if (boundSql != null && name.startsWith("set") && isParameterBinder(args)) {
            int idx = (Integer) args[0];
            Object value = args.length > 1 ? args[1] : null;
            currentParameters.put(idx, value);
        } else if ("clearParameters".equals(name)) {
            currentParameters.clear();
        } else if ("addBatch".equals(name)) {
            batchCount++;
            if (boundSql == null && args != null && args.length == 1 && args[0] instanceof String s) {
                lastBatchSql = s;
            }
        } else if ("clearBatch".equals(name)) {
            batchCount = 0;
            lastBatchSql = null;
        }

        if (isExecuteMethod(name)) {
            return timedInvoke(name, method, args);
        }

        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException ite) {
            throw ite.getCause();
        }
    }

    private Object timedInvoke(String name, Method method, Object[] args) throws Throwable {
        String sqlForEvent = resolveSqlForEvent(name, args);
        Object[] paramsForEvent = (boundSql != null) ? snapshotParameters() : null;
        long start = System.nanoTime();
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException ite) {
            throw ite.getCause();
        } finally {
            long duration = System.nanoTime() - start;
            watcher.onExecution(sqlForEvent, paramsForEvent, duration);
            if (name.equals("executeBatch") || name.equals("executeLargeBatch")) {
                batchCount = 0;
                lastBatchSql = null;
            }
        }
    }

    private String resolveSqlForEvent(String name, Object[] args) {
        if (boundSql != null) return boundSql;
        if (name.equals("executeBatch") || name.equals("executeLargeBatch")) {
            return lastBatchSql != null
                    ? "<batch: last=" + lastBatchSql + ">"
                    : "<batch: " + batchCount + " statements>";
        }
        if (args != null && args.length > 0 && args[0] instanceof String s) return s;
        return null;
    }

    private Object[] snapshotParameters() {
        if (currentParameters.isEmpty()) return new Object[0];
        Object[] out = new Object[currentParameters.lastKey()];
        for (Map.Entry<Integer, Object> e : currentParameters.entrySet()) {
            int idx = e.getKey() - 1;
            if (idx >= 0 && idx < out.length) out[idx] = e.getValue();
        }
        return out;
    }

    private static boolean isParameterBinder(Object[] args) {
        return args != null && args.length >= 1 && args[0] instanceof Integer;
    }

    private static boolean isExecuteMethod(String name) {
        return name.equals("execute")
                || name.equals("executeQuery")
                || name.equals("executeUpdate")
                || name.equals("executeBatch")
                || name.equals("executeLargeBatch")
                || name.equals("executeLargeUpdate");
    }
}
