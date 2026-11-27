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
import io.ddd4j.core.ddd.event.EntityId;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.spi.BeanManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * 验证 <code>HelidonDomainEventPublisher</code> 在<code>constructor</code>等操作中的行为契约。
 */
@ExtendWith(MockitoExtension.class)
class HelidonDomainEventPublisherTest {

    /**
     * 用于隔离 <code>BeanManager</code> 调用并记录交互的测试替身。
     */
    @Mock
    private BeanManager beanManager;

    /**
     * 用于隔离 <code>Event&lt;Object&gt;</code> 调用并记录交互的测试替身。
     */
    @Mock
    private Event<Object> event;

    /**
     * 在测试框架中执行 <code>beanManager.getEvent()</code>、<code>publisher.publish()</code>，验证该场景。
     */
    @Test
    void publishFiresEvent() {
        when(beanManager.getEvent()).thenReturn(event);
        HelidonDomainEventPublisher publisher = new HelidonDomainEventPublisher(beanManager);

        publisher.publish((Object) "payload");

        verify(event).fire("payload");
    }

    /**
     * 在测试框架中执行 <code>publisher.publish()</code>、<code>never()</code>、<code>org.mockito.ArgumentMatchers.any()</code>，验证该场景。
     */
    @Test
    void publishSkipsNullEvent() {
        HelidonDomainEventPublisher publisher = new HelidonDomainEventPublisher(beanManager);

        publisher.publish((Object) null);

        verify(event, never()).fire(org.mockito.ArgumentMatchers.any());
    }

    /**
     * 在测试框架中执行 <code>beanManager.getEvent()</code>、<code>publisher.publish()</code>，验证该场景。
     */
    @Test
    void publishDomainEventFiresTypedEvent() {
        when(beanManager.getEvent()).thenReturn(event);
        HelidonDomainEventPublisher publisher = new HelidonDomainEventPublisher(beanManager);
        DomainEvent<EntityId> domainEvent = new DomainEvent<EntityId>("orders.created") {
        };

        publisher.publish(domainEvent);

        verify(event).fire(domainEvent);
    }

    /**
     * 验证被测调用 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void constructorRejectsNullBeanManager() {
        assertThrows(NullPointerException.class, () -> new HelidonDomainEventPublisher(null));
    }
}
