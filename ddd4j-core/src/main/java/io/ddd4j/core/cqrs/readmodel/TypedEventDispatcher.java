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
import java.util.Collections;
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

    /** \u6309\u4e8b\u4ef6\u7c7b\u578b\u6807\u8bc6\u67e5\u627e\u7684\u5f3a\u7c7b\u578b\u5904\u7406\u5668\u3002 */
    private final Map<String, TypedEventHandler<?>> handlers;

    /**
     * 建立事件类型到处理器的索引。
     *
     * @param handlers 待注册的类型化事件处理器
     */
    public TypedEventDispatcher(Collection<? extends TypedEventHandler<?>> handlers) {
        this.handlers = indexHandlers(handlers);
    }

    /**
     * 将事件交给匹配类型的处理器。
     *
     * @param event 待派发事件
     * @return 找到并调用处理器时为 true
     */
    public boolean dispatch(TypedEvent event) {
        TypedEvent typedEvent = Objects.requireNonNull(event, "event must not be null");
        return dispatch(typedEvent.getEventType(), typedEvent);
    }

    /**
     * 逐个派发事件集合。
     *
     * @param events 待派发事件集合
     * @return 实际派发的事件数
     */
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

    /**
     * 将事件交给匹配类型的处理器。
     *
     * @param eventType 事件类型
     * @param event 事件载荷
     * @return 找到并调用处理器时为 true
     */
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

    /**
     * 统计已注册的处理器数。
     *
     * @return 处理器数量
     */
    public int size() {
        return handlers.size();
    }

    /**
     * \u6309\u5904\u7406\u5668\u58f0\u660e\u7684\u4e8b\u4ef6\u7c7b\u578b\u5efa\u7acb\u5206\u53d1\u67e5\u627e\u8868\u3002
     * @param eventHandlers \u5019\u9009\u4e8b\u4ef6\u5904\u7406\u5668\u96c6\u5408
     * @return \u6309\u4e8b\u4ef6\u7c7b\u578b\u7d22\u5f15\u7684\u5904\u7406\u5668\u6620\u5c04
     */
    private Map<String, TypedEventHandler<?>> indexHandlers(Collection<? extends TypedEventHandler<?>> eventHandlers) {
        Map<String, TypedEventHandler<?>> indexedHandlers = new LinkedHashMap<>();
        if (CollKit.isEmpty(eventHandlers)) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(indexedHandlers));
        }
        for (TypedEventHandler<?> handler : eventHandlers) {
            TypedEventHandler<?> safeHandler = Objects.requireNonNull(handler, "handler must not be null");
            if (StrKit.isBlank(safeHandler.getEventType())) {
                throw new IllegalArgumentException("handler eventType must not be blank");
            }
            indexedHandlers.put(safeHandler.getEventType(), safeHandler);
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(indexedHandlers));
    }

    /**
     * \u6821\u9a8c\u8fd0\u884c\u65f6\u4e8b\u4ef6\u7c7b\u578b\u5e76\u8c03\u7528\u5339\u914d\u7684\u5904\u7406\u5668\u3002
     * @param <E> \u5904\u7406\u5668\u63a5\u53d7\u7684\u4e8b\u4ef6\u7c7b\u578b
     * @param handler \u8d1f\u8d23\u8be5\u4e8b\u4ef6\u7684\u5f3a\u7c7b\u578b\u5904\u7406\u5668
     * @param event \u4ece\u5206\u53d1\u5165\u53e3\u6536\u5230\u7684\u4e8b\u4ef6\u5bf9\u8c61
     * @throws IllegalArgumentException \u4e8b\u4ef6\u4e0d\u662f\u5904\u7406\u5668\u58f0\u660e\u7684\u7c7b\u578b
     */
    private <E> void invoke(TypedEventHandler<E> handler, Object event) {
        if (!handler.getEventClass().isInstance(event)) {
            throw new IllegalArgumentException("event type mismatch: " + event.getClass().getName());
        }
        handler.handle(handler.getEventClass().cast(event));
    }
}
