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
import io.ddd4j.core.ddd.event.EntityId;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** 三线共同的 CDI 领域事件分派契约。 */
class CdiDomainEventPublisherContractTest {

    @Test
    void domainAndPlainObjectsFireOnceWhileNullIsIgnored() throws Exception {
        CdiDomainEventPublisher publisher = new CdiDomainEventPublisher();
        Field eventField = CdiDomainEventPublisher.class.getDeclaredField("event");
        eventField.setAccessible(true);
        List<Object> fired = new ArrayList<>();
        Object bus = Proxy.newProxyInstance(eventField.getType().getClassLoader(),
                new Class<?>[]{eventField.getType()}, (proxy, method, args) -> {
                    if ("fire".equals(method.getName())) {
                        fired.add(args[0]);
                        return null;
                    }
                    throw new IllegalStateException("Unexpected CDI method: " + method.getName());
                });
        eventField.set(publisher, bus);

        DomainEvent<EntityId> domainEvent = new DomainEvent<EntityId>() { };
        publisher.publish((Object) domainEvent);
        publisher.publish((Object) "plain");
        publisher.publish((Object) null);
        publisher.publish((DomainEvent<EntityId>) null);

        assertEquals(2, fired.size());
        assertSame(domainEvent, fired.get(0));
        assertEquals("plain", fired.get(1));
    }
}
