package io.ddd4j.core.cqrs.eventstore;

import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * 开发与测试使用的线程安全内存事件存储。
 *
 * <p>{@code readAll} 通过 {@link NavigableMap} 按全局 position 索引实现
 * O(limit) 复杂度（回填自 3.0.x 性能优化），避免全流扫描 + 排序。
 */
public final class InMemoryEventStore implements EventStore {
    private final Map<String, List<StoredEvent>> streams = new ConcurrentHashMap<String, List<StoredEvent>>();
    /** 全局 position → StoredEvent 索引（用于 readAll 高效分页）。 */
    private final NavigableMap<Long, StoredEvent> positionIndex = new ConcurrentSkipListMap<Long, StoredEvent>();
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
            long position = nextPosition++;
            event.setAggregateVersion(new io.ddd4j.core.ddd.event.AggregateVersion(version));
            StoredEvent stored = new StoredEvent(event.getEventId(), aggregateType, aggregateId, version, position,
                    event.getEventTimestamp(), event, event.getCorrelationId(), event.getCausationId());
            stream.add(stored);
            positionIndex.put(position, stored);
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
        List<StoredEvent> result = new ArrayList<StoredEvent>(Math.min(limit, positionIndex.size()));
        for (StoredEvent event : positionIndex.tailMap(fromPosition, true).values()) {
            result.add(event);
            if (result.size() == limit) break;
        }
        return result;
    }
}
