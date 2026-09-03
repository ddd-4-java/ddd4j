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
package io.ddd4j.spring.event;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.event.EntityId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import java.util.Objects;

/**
 * Spring 实现的领域事件发布者
 * <p>
 * 使用 Spring ApplicationEventPublisher 发布领域事件。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {

    /**
     * Spring 应用事件发布器
     */
    private final ApplicationEventPublisher publisher;

    public SpringDomainEventPublisher(ApplicationEventPublisher publisher) {
        log.debug("Initializing SpringDomainEventPublisher");
        Objects.requireNonNull(publisher, "ApplicationEventPublisher cannot be null");
        this.publisher = publisher;
    }

    @Override
    public <ID extends EntityId> void publish(DomainEvent<ID> event) {
        if (Objects.isNull(event)) {
            log.warn("Attempted to publish null domain event");
            return;
        }
        log.debug("Publishing domain event: {}", event.getClass().getSimpleName());
        publisher.publishEvent(event);
    }

    /**
     * 发布任意对象事件。
     * <p>
     * 若事件为 {@link DomainEvent} 实例，委托给 {@link #publish(DomainEvent)}；
     * 否则记录 warn 日志，明确告知只支持 DomainEvent，避免静默丢弃。
     * <p>
     * 与 Quarkus {@code CdiDomainEventPublisher} 行为对齐：覆写 core 默认 no-op，
     * 确保非 DomainEvent 对象不会被静默忽略。
     *
     * @param event 任意事件对象
     */
    @Override
    public void publish(Object event) {
        if (Objects.isNull(event)) {
            log.warn("Attempted to publish null event object");
            return;
        }
        if (event instanceof DomainEvent<?>) {
            DomainEvent<?> domainEvent = (DomainEvent<?>) event;
            publish(domainEvent);
        } else {
            log.warn("Published event is not a DomainEvent instance, " +
                    "only DomainEvent is supported. Event type: {}", event.getClass().getName());
        }
    }
}
