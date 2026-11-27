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

/**
 * 通过 <code>BaseContext.remove()</code>、<code>Contexts.getOrThrow()</code>、<code>Contexts.get()</code> 验证 <code>ContextLookup</code> 的运行行为。
 */
class ContextLookupTest {

    /**
     * 记录当前用例检查共享注册所使用的服务键。
     */
    private static final String[] sharedKeys = {
            SpiKeys.DOMAIN_EVENT_PUBLISHER,
            SpiKeys.MQ_EVENT_PUBLISHER
    };
    /**
     * 供不同用例定位同一项测试设置的键。
     */
    private final String testKey = "ddd4j.test." + UUID.randomUUID();

    /**
     * 测试执行前执行 <code>clearSharedKeys()</code> 完成资源准备。
     */
    @BeforeEach
    void setUp() {
        clearSharedKeys();
    }

    /**
     * 测试结束后执行 <code>clearSharedKeys()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void tearDown() {
        clearSharedKeys();
    }

    /**
     * 调用 <code>BaseContext.remove()</code>、<code>ThreadContext.remove()</code> 执行当前夹具操作。
     */
    private void clearSharedKeys() {
        BaseContext.remove(testKey);
        ThreadContext.remove(testKey);
        for (String sharedKey : sharedKeys) {
            BaseContext.remove(sharedKey);
            ThreadContext.remove(sharedKey);
        }
    }

    /**
     * 验证<code>Contexts.getOrThrow(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class)</code> 抛出 <code>IllegalStateException.class</code>。
     */
    @Test
    void shouldThrowOnMissingService() {
        BaseContext.remove(SpiKeys.DOMAIN_EVENT_PUBLISHER);
        assertThrows(IllegalStateException.class, () -> Contexts.getOrThrow(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class));
    }

    /**
     * 验证<code>Contexts.get(SpiKeys.MQ_EVENT_PUBLISHER, DomainEventPublisher.class).isPresent()</code>为假。
     */
    @Test
    void shouldReturnEmptyForMissingService() {
        BaseContext.remove(SpiKeys.MQ_EVENT_PUBLISHER);
        assertFalse(Contexts.get(SpiKeys.MQ_EVENT_PUBLISHER, DomainEventPublisher.class).isPresent());
    }

    /**
     * 验证<code>BaseContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, null)</code> 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void shouldRejectNullValue() {
        assertThrows(IllegalArgumentException.class,
                () -> BaseContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, null));
    }

    /**
     * 验证<code>BaseContext.contains(testKey)</code>为真，<code>BaseContext.contains(testKey)</code>为假。
     */
    @Test
    void shouldRemoveFromBaseContext() {
        BaseContext.inject(testKey, "value");
        assertTrue(BaseContext.contains(testKey));
        BaseContext.remove(testKey);
        assertFalse(BaseContext.contains(testKey));
    }

    /**
     * 验证<code>BaseContext.get(testKey, Object.class).orElse(null)</code> 与…为同一实例 <code>service</code>，<code>BaseContext.remove(testKey).orElse(null)</code> 与…为同一实例 <code>service</code>，<code>BaseContext.get(testKey)</code> 与…为同一实例 <code>service</code>。
     */
    @Test
    void plainContextWritesSupersedePendingRegistrationEvenForSameInstance() {
        Object service = new Object();
        BaseContext.Registration first = BaseContext.registerForRollback(testKey, service);
        BaseContext.inject(testKey, Object.class, service);
        first.rollback();
        assertSame(service, BaseContext.get(testKey, Object.class).orElse(null));

        BaseContext.Registration second = BaseContext.registerForRollback(testKey, service);
        assertSame(service, BaseContext.remove(testKey).orElse(null));
        second.rollback();
        assertFalse(BaseContext.contains(testKey));

        BaseContext.Registration third = BaseContext.registerForRollback(testKey, service);
        assertTrue(BaseContext.replaceIfCurrent(testKey, service, service));
        third.rollback();
        assertSame(service, BaseContext.get(testKey));
    }

    /**
     * 验证<code>BaseContext.get(testKey)</code> 与…为同一实例 <code>service</code>。
     */
    @Test
    void rollbackCanRestoreDisplacedRegistrationThatCommitted() {
        Object service = new Object();
        BaseContext.Registration first = BaseContext.registerForRollback(testKey, service);
        BaseContext.Registration second = BaseContext.registerForRollback(testKey, new Object());

        first.commit();
        first.rollback();
        second.rollback();
        second.rollback();

        assertSame(service, BaseContext.get(testKey));
    }

    /**
     * 验证<code>BaseContext.contains(testKey)</code>为假。
     */
    @Test
    void clearPreventsRollbackFromRestoringPreviousRegistrations() {
        BaseContext.inject(testKey, new Object());
        BaseContext.Registration first = BaseContext.registerForRollback(testKey, new Object());
        BaseContext.Registration second = BaseContext.registerForRollback(testKey, new Object());

        BaseContext.clear();
        first.rollback();
        second.rollback();

        assertFalse(BaseContext.contains(testKey));
    }
}
