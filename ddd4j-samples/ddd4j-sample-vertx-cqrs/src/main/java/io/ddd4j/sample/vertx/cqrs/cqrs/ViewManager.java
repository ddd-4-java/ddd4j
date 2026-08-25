package io.ddd4j.sample.vertx.cqrs.cqrs;



import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 视图管理器（CQRS 读侧）。
 *
 * <p>管理投影视图的注册和触发。
 */
public class ViewManager {

    private final List<ProjectionView> views = new ArrayList<>();
    private final InMemoryEventStore eventStore;
    private long lastPosition = 0;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public ViewManager(InMemoryEventStore eventStore) {
        this.eventStore = eventStore;
    }

    public void register(ProjectionView view) {
        views.add(view);
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::triggerOnce, 5, 5, TimeUnit.SECONDS);
    }

    public void triggerOnce() {
        List<InMemoryEventStore.StoredEvent> events = eventStore.readAll(lastPosition, 1000);
        if (!events.isEmpty()) {
            List<Object> payloads = events.stream()
                    .map(InMemoryEventStore.StoredEvent::event)
                    .toList();
            for (ProjectionView view : views) {
                view.handleEvents(payloads);
            }
            lastPosition = events.get(events.size() - 1).position() + 1;
        }
    }

    public void stop() {
        scheduler.shutdownNow();
    }
}
