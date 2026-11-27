/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.core.ddd.event;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 三线共同的领域事件类型、因果关系与策略过滤契约。
 */
class DomainEventContractTest {

    /**
     * 验证<code>first.getEventType().asString()</code> 等于 <code>"TestEvent"</code>，<code>second.getEventType()</code> 与…为同一实例 <code>first.getEventType()</code>，<code>first.getCausationId()</code>为空。
     */
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

    /**
     * 验证<code>child.getCorrelationId()</code> 等于 <code>original.getEventId()</code>，<code>child.getCausationId()</code> 等于 <code>original.getEventId()</code>，<code>grandchild.getCausationId()</code> 等于 <code>child.getEventId()</code>。
     */
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

    /**
     * 验证<code>event.supports("paid")</code>为假，<code>event.supports("created", "paid")</code>为真，<code>event.supports()</code>为假。
     */
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

    /**
     * 测试专用领域事件，用于控制事件类型和负载。
     */
    private static final class TestEvent extends DomainEvent<StringEntityId> {

        /**
         * 使用 <code>super()</code> 初始化 <code>TestEvent</code> 测试夹具。
         *
         * @param entityId 用于初始化 <code>TestEvent</code> 的 <code>String</code> 值
         */
        private TestEvent(String entityId) {
            super(entityId);
        }

        /**
         * 使用 <code>super()</code> 初始化 <code>TestEvent</code> 测试夹具。
         *
         * @param entityId  用于初始化 <code>TestEvent</code> 的 <code>String</code> 值
         * @param respondTo 用于初始化 <code>TestEvent</code> 的 <code>Event</code> 值
         */
        private TestEvent(String entityId, Event respondTo) {
            super(new EntityIdPath(new StringEntityId(entityId)), respondTo);
        }
    }
}
