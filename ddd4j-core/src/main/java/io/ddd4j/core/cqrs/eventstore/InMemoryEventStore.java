package io.ddd4j.core.cqrs.eventstore;

import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** 开发与测试使用的线程安全内存事件存储。 */
public final class InMemoryEventStore implements EventStore {
    private final Map<String, List<StoredEvent>> streams = new ConcurrentHashMap<String, List<StoredEvent>>();
    private long nextPosition = 1L;
    @Override public synchronized void append(String aggregateType, AggregateRootId aggregateId, List<? extends DomainEvent<?>> events, long expectedVersion) {
        Objects.requireNonNull(aggregateType, "aggregateType must not be null"); Objects.requireNonNull(aggregateId, "aggregateId must not be null"); Objects.requireNonNull(events, "events must not be null");
        if (events.isEmpty()) return;
        String key = aggregateType + "::" + aggregateId.asString();
        List<StoredEvent> stream = streams.get(key);
        long actualVersion = stream == null ? 0L : stream.size();
        if (actualVersion != expectedVersion) throw new AggregateVersionConflictException(aggregateType, aggregateId.asString(), expectedVersion, actualVersion);
        if (stream == null) { stream = new ArrayList<StoredEvent>(); streams.put(key, stream); }
        for (DomainEvent<?> event : events) {
            long version = stream.size() + 1L;
            event.setAggregateVersion(new io.ddd4j.core.ddd.event.AggregateVersion(version));
            stream.add(new StoredEvent(event.getEventId(), aggregateType, aggregateId, version, nextPosition++,
                    event.getEventTimestamp(), event, event.getCorrelationId(), event.getCausationId()));
        }
    }
    @Override public synchronized List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId) {
        Objects.requireNonNull(aggregateType, "aggregateType must not be null"); Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        List<StoredEvent> stream = streams.get(aggregateType + "::" + aggregateId.asString());
        return stream == null ? Collections.<StoredEvent>emptyList() : Collections.unmodifiableList(new ArrayList<StoredEvent>(stream));
    }
    @Override public synchronized List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId, long fromVersion, long toVersion) {
        List<StoredEvent> all = read(aggregateType, aggregateId); List<StoredEvent> result = new ArrayList<StoredEvent>();
        for (StoredEvent event : all) if (event.version() >= fromVersion && event.version() <= toVersion) result.add(event);
        return result;
    }
    @Override public synchronized List<StoredEvent> readAll(long fromPosition, int limit) {
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
        List<StoredEvent> result = new ArrayList<StoredEvent>();
        for (List<StoredEvent> stream : streams.values()) for (StoredEvent event : stream) if (event.position() >= fromPosition) result.add(event);
        Collections.sort(result, new java.util.Comparator<StoredEvent>() { @Override public int compare(StoredEvent left, StoredEvent right) { return Long.compare(left.position(), right.position()); } });
        return result.size() <= limit ? result : new ArrayList<StoredEvent>(result.subList(0, limit));
    }
}
