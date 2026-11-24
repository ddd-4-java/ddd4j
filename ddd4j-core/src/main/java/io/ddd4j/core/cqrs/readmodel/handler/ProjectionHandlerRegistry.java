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
package io.ddd4j.core.cqrs.readmodel.handler;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.kit.lang.StrKit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 按独立视图身份注册投影处理器。同一事件允许多个视图订阅，各自维护检查点。
 * 注册先验证完整声明，再原子发布不可变订阅快照；同名视图整批拒绝。
 * 旧单值查询确定性返回最先注册的订阅者，多视图调用使用 {@link #findHandlers(Class)}。
 */
public class ProjectionHandlerRegistry {

    private final Map<String, ProjectionHandler> handlers = new LinkedHashMap<>();
    private final Map<String, List<Class<? extends DomainEvent<?>>>> subscriptions = new LinkedHashMap<>();

    /**
     * 注册处理器。不同名称可订阅相同事件，同名不能覆盖已有注册。
     * @param handler 处理器及完整订阅声明
     * @throws IllegalStateException 视图名已注册
     */
    public synchronized void register(ProjectionHandler handler) {
        ProjectionHandler actual = Objects.requireNonNull(handler, "handler must not be null");
        String name = actual.getName();
        if (StrKit.isBlank(name)) {
            throw new IllegalArgumentException("handler name must not be blank");
        }
        List<Class<? extends DomainEvent<?>>> declaredTypes = new ArrayList<>(
                Objects.requireNonNull(actual.eventTypes(), "event types must not be null"));
        for (Class<?> type : declaredTypes) {
            Objects.requireNonNull(type, "event type must not be null");
        }
        List<Class<? extends DomainEvent<?>>> types = Collections.unmodifiableList(
                declaredTypes.stream().distinct().collect(Collectors.toList()));
        if (handlers.containsKey(name)) {
            throw new IllegalStateException("Duplicate projection handler registration for view: " + name);
        }
        handlers.put(name, actual);
        subscriptions.put(name, types);
    }

    /**
     * 校验本次适配快照与登记元数据一致；未注册对象可直接适配。
     * 按对象身份匹配，避免改名后用新名称绕过登记检查。
     */
    synchronized void validateMetadata(ProjectionHandler handler, String name,
                                       Collection<Class<? extends DomainEvent<?>>> types) {
        for (Map.Entry<String, ProjectionHandler> entry : handlers.entrySet()) {
            if (entry.getValue() == handler && (!Objects.equals(entry.getKey(), name)
                    || !new HashSet<>(subscriptions.get(entry.getKey())).equals(new HashSet<>(types)))) {
                throw new IllegalStateException("Registered projection metadata changed for view: " + entry.getKey());
            }
        }
    }

    /**
     * 返回按注册顺序排列的不可变快照，每个视图出现一次。
     * @return 全部已注册视图
     */
    public synchronized Collection<ProjectionHandler> all() {
        return Collections.unmodifiableList(new ArrayList<>(handlers.values()));
    }

    /**
     * 兼容旧单视图调用；多订阅时返回最先注册者，不执行扇出。
     * @param eventType 事件类型
     * @param <E> 事件类型
     * @return 第一个订阅者，未注册时为空
     */
    public <E extends DomainEvent<?>> Optional<ProjectionHandler> findHandler(Class<E> eventType) {
        return findHandlers(eventType).stream().findFirst();
    }

    /**
     * 查找全部独立订阅视图，按注册顺序返回不可变快照。
     * @param eventType 事件类型
     * @param <E> 事件类型
     * @return 订阅该类型的全部处理器
     */
    public synchronized <E extends DomainEvent<?>> List<ProjectionHandler> findHandlers(Class<E> eventType) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        return Collections.unmodifiableList(handlers.entrySet().stream()
                .filter(entry -> subscriptions.get(entry.getKey()).contains(eventType))
                .map(Map.Entry::getValue).collect(Collectors.toList()));
    }
}
