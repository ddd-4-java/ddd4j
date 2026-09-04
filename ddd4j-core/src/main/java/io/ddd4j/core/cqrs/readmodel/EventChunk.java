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
package io.ddd4j.core.cqrs.readmodel;

import java.util.ArrayList;
import io.ddd4j.kit.lang.CollKit;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 一批从事件流读取出的事件。
 *
 * @param <E> 事件类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Getter
public class EventChunk<E> {

    private final List<E> events;

    private final long nextEventNumber;

    public EventChunk(List<E> events, long nextEventNumber) {
        this.events = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(events, "events must not be null")));
        if (nextEventNumber < 0) {
            throw new IllegalArgumentException("nextEventNumber must not be negative");
        }
        this.nextEventNumber = nextEventNumber;
    }

    public static <E> EventChunk<E> empty(long nextEventNumber) {
        return new EventChunk<E>(Collections.<E>emptyList(), nextEventNumber);
    }

    public boolean hasEvents() {
        return CollKit.isNotEmpty(events);
    }
}
