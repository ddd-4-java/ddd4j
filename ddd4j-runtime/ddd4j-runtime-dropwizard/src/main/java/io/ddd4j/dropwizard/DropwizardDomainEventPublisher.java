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
package io.ddd4j.dropwizard;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.event.EntityId;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Dropwizard 显式监听器领域事件发布器。
 */
@Slf4j
public final class DropwizardDomainEventPublisher implements DomainEventPublisher {

    private final List<Consumer<Object>> listeners;

    /**
     * 使用指定事件监听器创建发布器。
     *
     * @param listeners 事件监听器
     */
    public DropwizardDomainEventPublisher(Collection<Consumer<Object>> listeners) {
        List<Consumer<Object>> copy = new ArrayList<>(Objects.requireNonNull(listeners, "listeners must not be null"));
        copy.forEach(listener -> Objects.requireNonNull(listener, "listener must not be null"));
        this.listeners = Collections.unmodifiableList(copy);
    }

    @Override
    public <ID extends EntityId> void publish(DomainEvent<ID> event) {
        publish((Object) event);
    }

    @Override
    public void publish(Object event) {
        if (Objects.nonNull(event)) {
            log.debug("Publishing Dropwizard event: {}", event.getClass().getName());
            listeners.forEach(listener -> listener.accept(event));
        }
    }
}
