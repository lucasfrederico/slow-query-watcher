package io.github.lucasfrederico.sqw;

/**
 * Captures the current stack trace, trimmed to the first user frame after the
 * proxy chain. Internal frames (this package + JDK reflection plumbing) are
 * skipped so the trace points to the application code that issued the query.
 */
final class StackTraceCapture {

    private static final String[] INTERNAL_PREFIXES = {
            "io.github.lucasfrederico.sqw.StackTraceCapture",
            "io.github.lucasfrederico.sqw.SlowQueryWatcher",
            "io.github.lucasfrederico.sqw.proxy.",
            "java.lang.Thread",
            "java.lang.reflect.",
            "jdk.internal.reflect.",
            "sun.reflect.",
            "com.sun.proxy.",
            "jdk.proxy"
    };

    private StackTraceCapture() {}

    static String[] capture(int maxFrames) {
        StackTraceElement[] raw = Thread.currentThread().getStackTrace();
        String[] out = new String[Math.min(maxFrames, raw.length)];
        int written = 0;
        for (StackTraceElement el : raw) {
            if (written == maxFrames) break;
            if (isInternal(el)) continue;
            out[written++] = el.toString();
        }
        if (written == out.length) return out;
        String[] trimmed = new String[written];
        System.arraycopy(out, 0, trimmed, 0, written);
        return trimmed;
    }

    private static boolean isInternal(StackTraceElement el) {
        String cn = el.getClassName();
        for (String prefix : INTERNAL_PREFIXES) {
            if (cn.startsWith(prefix)) return true;
        }
        return false;
    }
}
