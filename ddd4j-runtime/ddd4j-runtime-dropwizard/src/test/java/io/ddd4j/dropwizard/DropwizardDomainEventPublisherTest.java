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
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.StringEntityId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DropwizardDomainEventPublisherTest {

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

    @Test
    void publishSkipsNullEvent() {
        Consumer<Object> listener = mock(Consumer.class);
        DropwizardDomainEventPublisher publisher =
                new DropwizardDomainEventPublisher(Collections.singletonList(listener));

        publisher.publish((Object) null);

        org.mockito.Mockito.verifyNoInteractions(listener);
    }

    @Test
    void publishDomainEventNotifiesListeners() {
        Consumer<Object> listener = mock(Consumer.class);
        DropwizardDomainEventPublisher publisher =
                new DropwizardDomainEventPublisher(Collections.singletonList(listener));
        DomainEvent<EntityId> event = new DomainEvent<EntityId>(new EntityIdPath(new StringEntityId("orders"), new StringEntityId("created"))) {
        };

        publisher.publish(event);

        verify(listener).accept(event);
    }

    @Test
    void constructorRejectsNullAndSnapshotsListeners() {
        assertThrows(NullPointerException.class, () -> new DropwizardDomainEventPublisher(null));

        List<Consumer<Object>> mutable = new ArrayList<>();
        Consumer<Object> lateListener = mock(Consumer.class);
        DropwizardDomainEventPublisher publisher = new DropwizardDomainEventPublisher(mutable);
        mutable.add(lateListener);

        publisher.publish((Object) "payload");
        org.mockito.Mockito.verifyNoInteractions(lateListener);
    }
}
