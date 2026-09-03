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

import io.ddd4j.kit.lang.CollKit;
import io.ddd4j.kit.lang.StrKit;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 轻量类型事件分发器。
 *
 * <p>用于替代业务代码里大量 {@code instanceof} 分支，保持读侧投影处理逻辑清晰。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class TypedEventDispatcher {

    private final Map<String, TypedEventHandler<?>> handlers;

    public TypedEventDispatcher(Collection<? extends TypedEventHandler<?>> handlers) {
        this.handlers = indexHandlers(handlers);
    }

    public boolean dispatch(TypedEvent event) {
        TypedEvent typedEvent = Objects.requireNonNull(event, "event must not be null");
        return dispatch(typedEvent.getEventType(), typedEvent);
    }

    public int dispatchAll(Collection<? extends TypedEvent> events) {
        if (CollKit.isEmpty(events)) {
            return 0;
        }
        int dispatched = 0;
        for (TypedEvent event : events) {
            if (dispatch(event)) {
                dispatched++;
            }
        }
        return dispatched;
    }

    public boolean dispatch(String eventType, Object event) {
        if (StrKit.isBlank(eventType) || Objects.isNull(event)) {
            return false;
        }
        TypedEventHandler<?> handler = handlers.get(eventType);
        if (Objects.isNull(handler)) {
            return false;
        }
        invoke(handler, event);
        return true;
    }

    public int size() {
        return handlers.size();
    }

    private Map<String, TypedEventHandler<?>> indexHandlers(Collection<? extends TypedEventHandler<?>> eventHandlers) {
        Map<String, TypedEventHandler<?>> indexedHandlers = new LinkedHashMap<>();
        if (CollKit.isEmpty(eventHandlers)) {
            return Map.copyOf(indexedHandlers);
        }
        for (TypedEventHandler<?> handler : eventHandlers) {
            TypedEventHandler<?> safeHandler = Objects.requireNonNull(handler, "handler must not be null");
            if (StrKit.isBlank(safeHandler.getEventType())) {
                throw new IllegalArgumentException("handler eventType must not be blank");
            }
            indexedHandlers.put(safeHandler.getEventType(), safeHandler);
        }
        return Map.copyOf(indexedHandlers);
    }

    private <E> void invoke(TypedEventHandler<E> handler, Object event) {
        if (!handler.getEventClass().isInstance(event)) {
            throw new IllegalArgumentException("event type mismatch: " + event.getClass().getName());
        }
        handler.handle(handler.getEventClass().cast(event));
    }
}
