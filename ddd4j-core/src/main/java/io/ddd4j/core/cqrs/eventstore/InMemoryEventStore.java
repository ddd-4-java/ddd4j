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
    private final Map<String, List<DomainEvent<?>>> streams = new ConcurrentHashMap<String, List<DomainEvent<?>>>();
    @Override public synchronized void append(String aggregateType, AggregateRootId aggregateId, List<? extends DomainEvent<?>> events, long expectedVersion) {
        Objects.requireNonNull(aggregateType, "aggregateType must not be null"); Objects.requireNonNull(aggregateId, "aggregateId must not be null"); Objects.requireNonNull(events, "events must not be null");
        if (events.isEmpty()) return;
        String key = aggregateType + "::" + aggregateId.asString();
        List<DomainEvent<?>> stream = streams.get(key);
        long actualVersion = stream == null ? 0L : stream.size();
        if (actualVersion != expectedVersion) throw new IllegalStateException("Version conflict: expected " + expectedVersion + " but was " + actualVersion);
        if (stream == null) { stream = new ArrayList<DomainEvent<?>>(); streams.put(key, stream); }
        for (DomainEvent<?> event : events) { event.setAggregateVersion(new io.ddd4j.core.ddd.event.AggregateVersion(stream.size() + 1L)); stream.add(event); }
    }
    @Override public synchronized List<DomainEvent<?>> read(String aggregateType, AggregateRootId aggregateId) {
        Objects.requireNonNull(aggregateType, "aggregateType must not be null"); Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        List<DomainEvent<?>> stream = streams.get(aggregateType + "::" + aggregateId.asString());
        return stream == null ? Collections.<DomainEvent<?>>emptyList() : Collections.unmodifiableList(new ArrayList<DomainEvent<?>>(stream));
    }
}
