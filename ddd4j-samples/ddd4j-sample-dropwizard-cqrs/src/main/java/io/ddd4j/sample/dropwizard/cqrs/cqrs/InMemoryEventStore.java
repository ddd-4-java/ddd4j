package io.ddd4j.sample.dropwizard.cqrs.cqrs;



import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存事件存储（CQRS 写侧）。
 *
 * <p>按聚合 ID 存储事件列表，支持 append + read。
 */

public class InMemoryEventStore {

    private final Map<String, List<StoredEvent>> store = new ConcurrentHashMap<>();
    private final AtomicLong globalPosition = new AtomicLong(0);

    public synchronized void append(String aggregateId, List<Object> events, long expectedVersion) {
        List<StoredEvent> existing = store.computeIfAbsent(aggregateId, k -> new CopyOnWriteArrayList<>());
        long currentVersion = existing.size();
        if (currentVersion != expectedVersion) {
            throw new IllegalStateException("Version conflict: expected " + expectedVersion + " but was " + currentVersion);
        }
        for (Object event : events) {
            long position = globalPosition.incrementAndGet();
            existing.add(new StoredEvent(aggregateId, currentVersion++, event, position, Instant.now()));
        }
    }

    public List<StoredEvent> read(String aggregateId) {
        return store.getOrDefault(aggregateId, Collections.emptyList());
    }

    public List<StoredEvent> readAll(long fromPosition, int limit) {
        return store.values().stream()
                .flatMap(List::stream)
                .filter(e -> e.position() >= fromPosition)
                .sorted((a, b) -> Long.compare(a.position(), b.position()))
                .limit(limit)
                .toList();
    }

    public record StoredEvent(String aggregateId, long version, Object event, long position, Instant timestamp) {
    }
}
