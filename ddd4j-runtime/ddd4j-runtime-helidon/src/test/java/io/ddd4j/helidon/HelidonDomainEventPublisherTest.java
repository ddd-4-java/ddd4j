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

/** \u9a8c\u8bc1 <code>HelidonDomainEventPublisher</code> \u5728<code>constructor</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
@ExtendWith(MockitoExtension.class)
class HelidonDomainEventPublisherTest {

    /** \u7528\u4e8e\u9694\u79bb <code>BeanManager</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private BeanManager beanManager;

    /** \u7528\u4e8e\u9694\u79bb <code>Event&lt;Object&gt;</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private Event<Object> event;

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>beanManager.getEvent()</code>\u3001<code>publisher.publish()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void publishFiresEvent() {
        when(beanManager.getEvent()).thenReturn(event);
        HelidonDomainEventPublisher publisher = new HelidonDomainEventPublisher(beanManager);

        publisher.publish((Object) "payload");

        verify(event).fire("payload");
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>publisher.publish()</code>\u3001<code>never()</code>\u3001<code>org.mockito.ArgumentMatchers.any()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void publishSkipsNullEvent() {
        HelidonDomainEventPublisher publisher = new HelidonDomainEventPublisher(beanManager);

        publisher.publish((Object) null);

        verify(event, never()).fire(org.mockito.ArgumentMatchers.any());
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>beanManager.getEvent()</code>\u3001<code>publisher.publish()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void publishDomainEventFiresTypedEvent() {
        when(beanManager.getEvent()).thenReturn(event);
        HelidonDomainEventPublisher publisher = new HelidonDomainEventPublisher(beanManager);
        DomainEvent<EntityId> domainEvent = new DomainEvent<EntityId>("orders.created") {
        };

        publisher.publish(domainEvent);

        verify(event).fire(domainEvent);
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void constructorRejectsNullBeanManager() {
        assertThrows(NullPointerException.class, () -> new HelidonDomainEventPublisher(null));
    }
}
