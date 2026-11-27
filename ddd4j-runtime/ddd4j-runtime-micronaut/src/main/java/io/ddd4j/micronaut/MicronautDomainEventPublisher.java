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
package io.ddd4j.micronaut;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.event.EntityId;
import io.micronaut.context.event.ApplicationEventPublisher;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * 基于 Micronaut ApplicationEventPublisher 的进程内领域事件发布器。
 */
@Slf4j
public final class MicronautDomainEventPublisher implements DomainEventPublisher {

    /**
     * 将领域事件转交框架事件总线的发布器。
     */
    private final ApplicationEventPublisher<Object> publisher;

    /**
     * 使用 Micronaut 事件发布器创建领域事件适配器。
     *
     * @param publisher Micronaut 事件发布器
     */
    public MicronautDomainEventPublisher(ApplicationEventPublisher<Object> publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
    }

    /**
     * 将传入的事件交给当前发布通道。
     *
     * <p>Micronaut 实现调用 <code>publish((Object)event)</code>。</p>
     *
     * @param <ID>  实体标识类型
     * @param event 领域事件
     */
    @Override
    public <ID extends EntityId> void publish(DomainEvent<ID> event) {
        publish((Object) event);
    }

    /**
     * 将传入的事件交给当前发布通道。
     *
     * <p>Micronaut 实现调用 <code>log.debug("Publishing Micronaut event: {}", event.getClass().getName())</code>。</p>
     *
     * @param event 任意事件对象
     */
    @Override
    public void publish(Object event) {
        if (Objects.nonNull(event)) {
            log.debug("Publishing Micronaut event: {}", event.getClass().getName());
            publisher.publishEvent(event);
        }
    }
}
