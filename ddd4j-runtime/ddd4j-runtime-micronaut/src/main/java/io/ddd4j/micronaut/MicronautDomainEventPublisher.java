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

    /** \u5c06\u9886\u57df\u4e8b\u4ef6\u8f6c\u4ea4\u6846\u67b6\u4e8b\u4ef6\u603b\u7ebf\u7684\u53d1\u5e03\u5668\u3002 */
    private final ApplicationEventPublisher<Object> publisher;

    /**
     * \u4f7f\u7528 Micronaut \u4e8b\u4ef6\u53d1\u5e03\u5668\u521b\u5efa\u9886\u57df\u4e8b\u4ef6\u9002\u914d\u5668\u3002
     * @param publisher Micronaut \u4e8b\u4ef6\u53d1\u5e03\u5668
     */
    public MicronautDomainEventPublisher(ApplicationEventPublisher<Object> publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
    }

    /**
     * 发布数据。
     * @param <ID> 类型参数。
     * @param event 事件消息。
     */
    @Override
    public <ID extends EntityId> void publish(DomainEvent<ID> event) {
        publish((Object) event);
    }

    /**
     * 发布数据。
     * @param event 事件消息。
     */
    @Override
    public void publish(Object event) {
        if (Objects.nonNull(event)) {
            log.debug("Publishing Micronaut event: {}", event.getClass().getName());
            publisher.publishEvent(event);
        }
    }
}
