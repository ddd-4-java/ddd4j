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

    /** \u9a8c\u8bc1<code>first.getEventType().asString()</code> \u7b49\u4e8e <code>"TestEvent"</code>\uff0c<code>second.getEventType()</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>first.getEventType()</code>\uff0c<code>first.getCausationId()</code>\u4e3a\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>child.getCorrelationId()</code> \u7b49\u4e8e <code>original.getEventId()</code>\uff0c<code>child.getCausationId()</code> \u7b49\u4e8e <code>original.getEventId()</code>\uff0c<code>grandchild.getCausationId()</code> \u7b49\u4e8e <code>child.getEventId()</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>event.supports("paid")</code>\u4e3a\u5047\uff0c<code>event.supports("created", "paid")</code>\u4e3a\u771f\uff0c<code>event.supports()</code>\u4e3a\u5047\u3002 */
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

    /** \u6d4b\u8bd5\u4e13\u7528\u9886\u57df\u4e8b\u4ef6\uff0c\u7528\u4e8e\u63a7\u5236\u4e8b\u4ef6\u7c7b\u578b\u548c\u8d1f\u8f7d\u3002 */
    private static final class TestEvent extends DomainEvent<StringEntityId> {

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TestEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param entityId \u7528\u4e8e\u521d\u59cb\u5316 <code>TestEvent</code> \u7684 <code>String</code> \u503c
         */
        private TestEvent(String entityId) {
            super(entityId);
        }

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TestEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param entityId \u7528\u4e8e\u521d\u59cb\u5316 <code>TestEvent</code> \u7684 <code>String</code> \u503c
         * @param respondTo \u7528\u4e8e\u521d\u59cb\u5316 <code>TestEvent</code> \u7684 <code>Event</code> \u503c
         */
        private TestEvent(String entityId, Event respondTo) {
            super(new EntityIdPath(new StringEntityId(entityId)), respondTo);
        }
    }
}
