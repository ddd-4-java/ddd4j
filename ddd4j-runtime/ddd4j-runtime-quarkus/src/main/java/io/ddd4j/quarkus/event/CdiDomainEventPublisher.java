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
package io.ddd4j.quarkus.event;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.event.EntityId;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import java.util.Objects;

/**
 * Quarkus CDI 实现的领域事件发布者
 * <p>
 * 使用 CDI {@code Event<DomainEvent>} 发布领域事件。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
@DefaultBean
@ApplicationScoped
public class CdiDomainEventPublisher implements DomainEventPublisher {

    /**
     * CDI 事件总线
     */
    @Inject
    Event<Object> event;

    @Override
    public <ID extends EntityId> void publish(DomainEvent<ID> domainEvent) {
        if (Objects.isNull(domainEvent)) {
            log.warn("Attempted to publish null domain event");
            return;
        }
        log.debug("Publishing domain event: {}", domainEvent.getClass().getSimpleName());
        event.fire(domainEvent);
    }
}
