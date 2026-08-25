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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存事件存储默认实现（CQRS 写侧）。
 *
 * <p>按聚合 ID 存储事件列表，支持 append + read + readAll。
 * 并发安全：{@code append} 使用 {@code synchronized} 保证乐观版本校验的原子性，
 * {@code read} / {@code readAll} 无锁读取。
 *
 * <h3>性能优化（M19）</h3>
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
    private final NavigableMap<Long, StoredEvent> positionIndex = new java.util.concurrent.ConcurrentSkipListMap<>();

    private final AtomicLong globalPosition = new AtomicLong(0);

    @Override
    public synchronized void append(String aggregateId, List<Object> events, long expectedVersion) {
        List<StoredEvent> existing = store.computeIfAbsent(aggregateId, k -> new CopyOnWriteArrayList<>());
        long currentVersion = existing.size();
        if (currentVersion != expectedVersion) {
            throw new IllegalStateException(
                    "Version conflict: expected " + expectedVersion + " but was " + currentVersion);
        }
        for (Object event : events) {
            long position = globalPosition.incrementAndGet();
            StoredEvent storedEvent = new StoredEvent(aggregateId, currentVersion++, event, position, Instant.now());
            existing.add(storedEvent);
            positionIndex.put(position, storedEvent);
        }
    }

    @Override
    public List<StoredEvent> read(String aggregateId) {
        return store.getOrDefault(aggregateId, Collections.emptyList());
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
}
