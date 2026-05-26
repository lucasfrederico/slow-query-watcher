package io.github.lucasfrederico.sqw.spring;

import io.github.lucasfrederico.sqw.SlowQueryEvent;
import io.github.lucasfrederico.sqw.SlowQueryListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Test-only listener that keeps every event it sees. */
public class RecordingListener implements SlowQueryListener {

    private final List<SlowQueryEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void onSlowQuery(SlowQueryEvent event) {
        events.add(event);
    }

    public List<SlowQueryEvent> events() {
        return events;
    }

    public void clear() {
        events.clear();
    }
}
