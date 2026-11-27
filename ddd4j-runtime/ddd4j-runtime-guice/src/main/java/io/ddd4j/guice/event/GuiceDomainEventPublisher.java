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
package io.ddd4j.guice.event;

import com.google.common.eventbus.EventBus;
import com.google.inject.Inject;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.event.EntityId;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Guice 实现的领域事件发布者
 * <p>
 * 使用 Google Guava {@link EventBus} 发布进程内领域事件。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
public class GuiceDomainEventPublisher implements DomainEventPublisher {

    /**
     * Guava 事件总线
     */
    private final EventBus eventBus;

    /**
     * \u4f7f\u7528 Guava EventBus \u521b\u5efa\u9886\u57df\u4e8b\u4ef6\u53d1\u5e03\u5668\u3002
     * @param eventBus Guava \u4e8b\u4ef6\u603b\u7ebf
     */
    @Inject
    public GuiceDomainEventPublisher(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * 将传入的事件交给当前发布通道。
     *
     * <p>此实现调用 <code>log.warn("Attempted to publish null domain event")</code>。</p>
     *
     * @param <ID> 实体标识类型
     * @param event 领域事件
     */
    @Override
    public <ID extends EntityId> void publish(DomainEvent<ID> event) {
        if (Objects.isNull(event)) {
            log.warn("Attempted to publish null domain event");
            return;
        }
        log.debug("Publishing domain event: {}", event.getClass().getSimpleName());
        eventBus.post(event);
    }

}
