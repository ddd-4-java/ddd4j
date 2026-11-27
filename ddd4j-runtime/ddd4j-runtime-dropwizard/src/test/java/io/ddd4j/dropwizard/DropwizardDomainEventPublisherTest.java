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
import io.ddd4j.core.ddd.event.EntityId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** \u9a8c\u8bc1 <code>DropwizardDomainEventPublisher</code> \u5728<code>constructor</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class DropwizardDomainEventPublisherTest {

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>mock()</code>\u3001<code>Arrays.asList()</code>\u3001<code>publisher.publish()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void publishNotifiesAllListeners() {
        Consumer<Object> first = mock(Consumer.class);
        Consumer<Object> second = mock(Consumer.class);
        DropwizardDomainEventPublisher publisher =
                new DropwizardDomainEventPublisher(Arrays.asList(first, second));

        publisher.publish((Object) "payload");

        verify(first).accept("payload");
        verify(second).accept("payload");
    }

    /** \u9a8c\u8bc1\u672a\u8c03\u7528 <code>listener</code>\u3002 */
    @Test
    void publishSkipsNullEvent() {
        Consumer<Object> listener = mock(Consumer.class);
        DropwizardDomainEventPublisher publisher =
                new DropwizardDomainEventPublisher(Collections.singletonList(listener));

        publisher.publish((Object) null);

        org.mockito.Mockito.verifyNoInteractions(listener);
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>mock()</code>\u3001<code>Collections.singletonList()</code>\u3001<code>publisher.publish()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void publishDomainEventNotifiesListeners() {
        Consumer<Object> listener = mock(Consumer.class);
        DropwizardDomainEventPublisher publisher =
                new DropwizardDomainEventPublisher(Collections.singletonList(listener));
        DomainEvent<EntityId> event = new DomainEvent<EntityId>("orders.created") {
        };

        publisher.publish(event);

        verify(listener).accept(event);
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void constructorRejectsNullAndSnapshotsListeners() {
        assertThrows(NullPointerException.class, () -> new DropwizardDomainEventPublisher(null));
        assertThrows(NullPointerException.class,
                () -> new DropwizardDomainEventPublisher(Collections.singletonList(null)));

        List<Consumer<Object>> mutable = new ArrayList<>();
        Consumer<Object> lateListener = mock(Consumer.class);
        DropwizardDomainEventPublisher publisher = new DropwizardDomainEventPublisher(mutable);
        mutable.add(lateListener);

        publisher.publish((Object) "payload");
        org.mockito.Mockito.verifyNoInteractions(lateListener);
    }
}
