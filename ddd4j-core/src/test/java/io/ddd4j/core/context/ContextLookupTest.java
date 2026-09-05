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
package io.ddd4j.core.context;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContextLookupTest {

    private static final String[] sharedKeys = {
            SpiKeys.DOMAIN_EVENT_PUBLISHER,
            SpiKeys.MQ_EVENT_PUBLISHER
    };
    private final String testKey = "ddd4j.test." + UUID.randomUUID();

    @BeforeEach
    void setUp() {
        clearSharedKeys();
    }

    @AfterEach
    void tearDown() {
        clearSharedKeys();
    }

    private void clearSharedKeys() {
        BaseContext.remove(testKey);
        ThreadContext.remove(testKey);
        for (String sharedKey : sharedKeys) {
            BaseContext.remove(sharedKey);
            ThreadContext.remove(sharedKey);
        }
    }

    @Test
    void shouldThrowOnMissingService() {
        BaseContext.remove(SpiKeys.DOMAIN_EVENT_PUBLISHER);
        assertThrows(IllegalStateException.class, () -> Contexts.getOrThrow(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class));
    }

    @Test
    void shouldReturnEmptyForMissingService() {
        BaseContext.remove(SpiKeys.MQ_EVENT_PUBLISHER);
        assertTrue(Contexts.get(SpiKeys.MQ_EVENT_PUBLISHER, DomainEventPublisher.class).isEmpty());
    }

    @Test
    void shouldRejectNullValue() {
        assertThrows(IllegalArgumentException.class,
                () -> BaseContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, null));
    }

    @Test
    void shouldRemoveFromBaseContext() {
        BaseContext.inject(testKey, "value");
        assertTrue(BaseContext.contains(testKey));
        BaseContext.remove(testKey);
        assertFalse(BaseContext.contains(testKey));
    }
}
