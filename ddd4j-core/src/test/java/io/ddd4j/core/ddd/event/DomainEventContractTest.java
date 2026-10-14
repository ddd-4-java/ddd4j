/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.core.ddd.event;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 三线共同的领域事件类型、因果关系与策略过滤契约。 */
class DomainEventContractTest {

    @Test
    void eventTypeAndSourceAreDerivedFromTheEventClassAndPath() {
        TestEvent first = new TestEvent("order-1");
        TestEvent second = new TestEvent("order-2");

        assertEquals("TestEvent", first.getEventType().asString());
        assertSame(first.getEventType(), second.getEventType());
        assertEquals("order-1", first.source());
        assertEquals(new StringEntityId("order-1"), first.getEntityIdPath().last());
        assertNotNull(first.getEventId());
        assertNotNull(first.getEventTimestamp());
        assertNull(first.getCorrelationId());
        assertNull(first.getCausationId());
    }

    @Test
    void chainedEventKeepsTheOriginalCorrelationAndImmediateCause() {
        TestEvent original = new TestEvent("order-1");
        TestEvent child = new TestEvent("order-1", original);
        TestEvent grandchild = new TestEvent("order-1", child);

        assertEquals(original.getEventId(), child.getCorrelationId());
        assertEquals(original.getEventId(), child.getCausationId());
        assertEquals(original.getEventId(), grandchild.getCorrelationId());
        assertEquals(child.getEventId(), grandchild.getCausationId());
    }

    @Test
    void strategyKeysRequireAnIntersection() {
        TestEvent event = new TestEvent("order-1");
        assertFalse(event.supports("paid"));
        event.setSupportKeys(new HashSet<>(Arrays.asList("paid", "shipped")));

        assertTrue(event.supports("created", "paid"));
        assertFalse(event.supports("cancelled"));
        assertFalse(event.supports((String[]) null));
        assertFalse(event.supports());
    }

    private static final class TestEvent extends DomainEvent<StringEntityId> {

        private TestEvent(String entityId) {
            super(entityId);
        }

        private TestEvent(String entityId, Event respondTo) {
            super(new EntityIdPath(new StringEntityId(entityId)), respondTo);
        }
    }
}
