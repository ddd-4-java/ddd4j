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
package io.ddd4j.sample.micronaut.cqrs.readmodel;

import io.ddd4j.core.cqrs.eventstore.InMemoryEventStore;
import io.ddd4j.core.cqrs.eventstore.StoredEvent;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.cqrs.readmodel.EventChunk;
import io.ddd4j.core.cqrs.readmodel.EventChunkReader;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 基于 {@link InMemoryEventStore} 的事件块读取器。
 *
 * <p>适配 core {@link EventChunkReader} 接口，
 * 从 InMemoryEventStore 按全局 position 分页读取事件。
 */
public class InMemoryEventChunkReader implements EventChunkReader<Object> {

    private final InMemoryEventStore eventStore;

    public InMemoryEventChunkReader(InMemoryEventStore eventStore) {
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore must not be null");
    }

    @Override
    public EventChunk<Object> read(String streamId, long fromEventNumber, int chunkSize,
                                   Collection<String> eventTypes) {
        List<StoredEvent> storedEvents = eventStore.readAll(fromEventNumber, chunkSize);
        if (storedEvents.isEmpty()) {
            return EventChunk.empty(fromEventNumber);
        }
        List<Object> payloads = storedEvents.stream()
                .map(e -> (Object) e.payload())
                .toList();
        long nextPos = storedEvents.get(storedEvents.size() - 1).position() + 1;
        return new EventChunk<>(payloads, nextPos);
    }
}
