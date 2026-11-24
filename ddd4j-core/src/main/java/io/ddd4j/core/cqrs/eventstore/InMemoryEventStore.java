/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.core.cqrs.eventstore;

import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内存事件存储默认实现（CQRS 写侧）。
 *
 * <p>按聚合类型与聚合 ID 存储事件列表，支持 append + read + readAll。
 * 并发安全：{@code append} 使用 {@code synchronized} 保证乐观版本校验的原子性，
 * {@code read} / {@code readAll} 无锁读取。
 *
 * <p><b>性能优化（M19）</b></p>
 * <p>{@code readAll} 通过 {@link NavigableMap} 按全局 position 索引，
 * 实现 O(limit) 复杂度，避免全量扫描 + 排序。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public class InMemoryEventStore implements EventStore {

    private final Map<String, List<StoredEvent>> store = new ConcurrentHashMap<>();

    /**
     * 全局 position → StoredEvent 索引（用于 readAll 高效分页）。
     */
    private final NavigableMap<Long, StoredEvent> positionIndex = new ConcurrentSkipListMap<>();

    private long globalPosition;

    @Override
    public synchronized void append(String aggregateType, AggregateRootId aggregateId,
                                    List<? extends DomainEvent<?>> events, long expectedVersion) {
        String streamKey = streamKey(aggregateType, aggregateId);
        verifyVersion(streamKey, aggregateType, aggregateId, expectedVersion);
        // 先构造完整批次，确保事件无效时不会写入半批或消耗全局位置。
        long initialPosition = globalPosition;
        List<StoredEvent> batch = new ArrayList<>(events.size());
        long version = expectedVersion;
        long position = initialPosition;
        for (DomainEvent<?> event : events) {
            batch.add(new StoredEvent(
                    event.getEventId(),
                    aggregateType,
                    aggregateId,
                    ++version,
                    ++position,
                    ZonedDateTime.now(),
                    event,
                    event.getCorrelationId(),
                    event.getCausationId()));
        }

        // 元数据读取可能重入追加。提交前复核流版本，并避开嵌套提交已经使用的位置。
        verifyVersion(streamKey, aggregateType, aggregateId, expectedVersion);
        if (globalPosition != initialPosition) {
            position = globalPosition;
            for (int i = 0; i < batch.size(); i++) {
                StoredEvent event = batch.get(i);
                batch.set(i, new StoredEvent(event.eventId(), aggregateType, aggregateId, event.version(),
                        ++position, event.timestamp(), event.payload(), event.correlationId(), event.causationId()));
            }
        }

        store.computeIfAbsent(streamKey, k -> new CopyOnWriteArrayList<>()).addAll(batch);
        for (StoredEvent storedEvent : batch) {
            positionIndex.put(storedEvent.position(), storedEvent);
        }
        globalPosition = position;
    }

    @Override
    public List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId) {
        return store.getOrDefault(streamKey(aggregateType, aggregateId), Collections.emptyList());
    }

    @Override
    public List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId, long fromVersion, long toVersion) {
        List<StoredEvent> events = read(aggregateType, aggregateId);
        List<StoredEvent> result = new ArrayList<>();
        for (StoredEvent event : events) {
            if (event.version() >= fromVersion && event.version() <= toVersion) {
                result.add(event);
            }
        }
        return result;
    }

    @Override
    public List<StoredEvent> readAll(long fromPosition, int limit) {
        NavigableMap<Long, StoredEvent> tail = positionIndex.tailMap(fromPosition, true);
        List<StoredEvent> result = new ArrayList<>(Math.min(limit, tail.size()));
        int count = 0;
        for (StoredEvent event : tail.values()) {
            if (count >= limit) {
                break;
            }
            result.add(event);
            count++;
        }
        return result;
    }

    private void verifyVersion(String streamKey, String aggregateType, AggregateRootId aggregateId,
                               long expectedVersion) {
        long actualVersion = store.getOrDefault(streamKey, Collections.emptyList()).size();
        if (actualVersion != expectedVersion) {
            throw new AggregateVersionConflictException(aggregateType, aggregateId.asString(), expectedVersion, actualVersion);
        }
    }

    private String streamKey(String aggregateType, AggregateRootId aggregateId) {
        return aggregateType + "#" + aggregateId.asTypedString();
    }
}
