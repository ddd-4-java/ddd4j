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
package io.ddd4j.sample.helidon.cqrs.cqrs;



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
