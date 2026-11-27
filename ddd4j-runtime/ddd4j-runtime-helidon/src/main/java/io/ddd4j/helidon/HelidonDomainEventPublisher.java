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
package io.ddd4j.helidon;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.event.EntityId;
import jakarta.enterprise.inject.spi.BeanManager;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * 基于 Helidon MP CDI 事件总线的领域事件发布器。
 */
@Slf4j
public final class HelidonDomainEventPublisher implements DomainEventPublisher {

    /** \u89e3\u6790 CDI Bean \u5e76\u89e6\u53d1\u5bb9\u5668\u4e8b\u4ef6\u7684\u7ba1\u7406\u5668\u3002 */
    private final BeanManager beanManager;

    /**
     * 使用 CDI BeanManager 创建领域事件发布器。
     *
     * @param beanManager CDI BeanManager
     */
    public HelidonDomainEventPublisher(BeanManager beanManager) {
        this.beanManager = Objects.requireNonNull(beanManager, "beanManager must not be null");
    }

    /**
     * 将传入的事件交给当前发布通道。
     *
     * <p>Helidon 实现调用 <code>publish((Object)event)</code>。</p>
     *
     * @param <ID> 实体标识类型
     * @param event 领域事件
     */
    @Override
    public <ID extends EntityId> void publish(DomainEvent<ID> event) {
        publish((Object) event);
    }

    /**
     * 将传入的事件交给当前发布通道。
     *
     * <p>Helidon 实现调用 <code>log.debug("Publishing Helidon CDI event: {}", event.getClass().getName())</code>。</p>
     *
     * @param event 任意事件对象
     */
    @Override
    public void publish(Object event) {
        if (Objects.nonNull(event)) {
            log.debug("Publishing Helidon CDI event: {}", event.getClass().getName());
            beanManager.getEvent().fire(event);
        }
    }
}
