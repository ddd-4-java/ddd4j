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
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.event.EntityId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link BaseContext}, {@link ThreadContext}, and {@link Contexts}.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class ContextTest {

    /** \u4f9b\u4e0d\u540c\u7528\u4f8b\u5b9a\u4f4d\u540c\u4e00\u9879\u6d4b\u8bd5\u8bbe\u7f6e\u7684\u952e\u3002 */
    private String testKey;

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>UUID.randomUUID()</code>\u3001<code>BaseContext.clear()</code>\u3001<code>ThreadContext.clear()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void setUp() {
        testKey = "ddd4j.test." + UUID.randomUUID();
        BaseContext.clear();
        ThreadContext.clear();
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>BaseContext.clear()</code>\u3001<code>ThreadContext.clear()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        BaseContext.clear();
        ThreadContext.clear();
    }

    /**
     * \u6784\u9020 <code>DomainEventPublisher</code> \u4f5c\u4e3a\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u6216\u7ed3\u679c\u3002
     * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>DomainEventPublisher</code> \u7ed3\u679c
     */
    private DomainEventPublisher stub(String name) {
        return new DomainEventPublisher() {
            /**
             * 发布数据。
             * @param <ID> 类型参数。
             * @param event 事件消息。
             */
            @Override
            public <ID extends EntityId> void publish(DomainEvent<ID> event) {

            }

            /**
             * 发布数据。
             * @param event 事件消息。
             */
            @Override
            public void publish(Object event) {
            }

            /**
             * publishAll() 方法。
             * @param <ID> 类型参数。
             * @param domainEvents 集合。
             */
            @Override
            public <ID extends EntityId> void publishAll(Collection<DomainEvent<ID>> domainEvents) {
            }

            /**
             * 返回对象的字符串表示。
             * @return 对象的字符串表示。
             */
            @Override
            public String toString() {
                return name;
            }
        };
    }

    // ========================= BaseContext =========================

    /** \u9a8c\u8bc1<code>resolved</code> \u5305\u542b <code>publisher</code>\u3002 */
    @Test
    void baseContext_injectTyped_shouldLookupBySameType() {
        DomainEventPublisher publisher = stub("p1");
        BaseContext.inject(testKey, DomainEventPublisher.class, publisher);

        Optional<DomainEventPublisher> resolved = BaseContext.get(testKey, DomainEventPublisher.class);

        assertThat(resolved).contains(publisher);
    }

    /** \u9a8c\u8bc1<code>BaseContext.inject(testKey, DomainEventPublisher.class, null)</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void baseContext_injectTyped_shouldRejectNullValue() {
        assertThatThrownBy(() -> BaseContext.inject(testKey, DomainEventPublisher.class, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** \u9a8c\u8bc1<code>BaseContext.inject(null, DomainEventPublisher.class, stub("p"))</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void baseContext_injectTyped_shouldRejectNullKey() {
        assertThatThrownBy(() -> BaseContext.inject(null, DomainEventPublisher.class, stub("p")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** \u9a8c\u8bc1<code>BaseContext.inject(testKey, targetType, "not-a-publisher")</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void baseContext_injectTyped_shouldRejectTypeMismatch() {
        // Use a raw-typed reference to defeat generic inference so the runtime isInstance check fires.
        Class targetType = DomainEventPublisher.class;
        assertThatThrownBy(() -> BaseContext.inject(testKey, targetType, "not-a-publisher"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** \u9a8c\u8bc1<code>BaseContext.get(testKey, Integer.class)</code>\u4e3a\u7a7a\u3002 */
    @Test
    void baseContext_injectTyped_shouldReturnEmptyForTypeMismatch() {
        BaseContext.inject(testKey, "value");

        assertThat(BaseContext.get(testKey, Integer.class)).isEmpty();
    }

    /** \u9a8c\u8bc1<code>(String) BaseContext.get(testKey)</code> \u7b49\u4e8e <code>"value"</code>\uff0c<code>BaseContext.contains(testKey)</code>\u4e3a\u771f\u3002 */
    @Test
    void baseContext_injectUntyped_shouldStoreAndRetrieveValue() {
        BaseContext.inject(testKey, "value");

        assertThat((String) BaseContext.get(testKey)).isEqualTo("value");
        assertThat(BaseContext.contains(testKey)).isTrue();
    }

    /** \u9a8c\u8bc1<code>(Object) BaseContext.get("missing")</code>\u4e3a\u7a7a\u3002 */
    @Test
    void baseContext_get_shouldReturnNullForMissingKey() {
        assertThat((Object) BaseContext.get("missing")).isNull();
    }

    /** \u9a8c\u8bc1<code>BaseContext.get("missing", "default")</code> \u7b49\u4e8e <code>"default"</code>\u3002 */
    @Test
    void baseContext_getWithDefault_shouldReturnDefaultForMissingKey() {
        assertThat(BaseContext.get("missing", "default")).isEqualTo("default");
    }

    /** \u9a8c\u8bc1<code>BaseContext.contains(null)</code>\u4e3a\u5047\u3002 */
    @Test
    void baseContext_contains_shouldReturnFalseForNullKey() {
        assertThat(BaseContext.contains(null)).isFalse();
    }

    /** \u9a8c\u8bc1<code>BaseContext.contains(testKey)</code>\u4e3a\u771f\uff0c<code>BaseContext.contains(testKey)</code>\u4e3a\u5047\u3002 */
    @Test
    void baseContext_remove_shouldDeleteEntry() {
        BaseContext.inject(testKey, "value");
        assertThat(BaseContext.contains(testKey)).isTrue();

        BaseContext.remove(testKey);

        assertThat(BaseContext.contains(testKey)).isFalse();
    }

    /** \u9a8c\u8bc1<code>BaseContext.contains(testKey)</code>\u4e3a\u5047\u3002 */
    @Test
    void baseContext_clear_shouldRemoveAllEntries() {
        BaseContext.inject(testKey, "v1");
        BaseContext.inject(testKey + "2", "v2");

        BaseContext.clear();

        assertThat(BaseContext.contains(testKey)).isFalse();
    }

    // ========================= ThreadContext =========================

    /** \u9a8c\u8bc1<code>resolved</code> \u5305\u542b <code>publisher</code>\u3002 */
    @Test
    void threadContext_injectTyped_shouldLookupOnSameThread() {
        DomainEventPublisher publisher = stub("tp");
        ThreadContext.inject(testKey, DomainEventPublisher.class, publisher);

        Optional<DomainEventPublisher> resolved = ThreadContext.get(testKey, DomainEventPublisher.class);

        assertThat(resolved).contains(publisher);
    }

    /** \u9a8c\u8bc1<code>ThreadContext.get(testKey, Integer.class)</code>\u4e3a\u7a7a\u3002 */
    @Test
    void threadContext_injectTyped_shouldReturnEmptyForTypeMismatch() {
        ThreadContext.inject(testKey, String.class, "value");

        assertThat(ThreadContext.get(testKey, Integer.class)).isEmpty();
    }

    /** \u9a8c\u8bc1<code>ThreadContext.get(testKey, String.class)</code>\u4e3a\u7a7a\u3002 */
    @Test
    void threadContext_injectTyped_shouldReturnEmptyForMissing() {
        assertThat(ThreadContext.get(testKey, String.class)).isEmpty();
    }

    /** \u9a8c\u8bc1<code>(String) ThreadContext.get(testKey)</code> \u7b49\u4e8e <code>"value"</code>\u3002 */
    @Test
    void threadContext_set_shouldStoreValue() {
        ThreadContext.set(testKey, "value");

        assertThat((String) ThreadContext.get(testKey)).isEqualTo("value");
    }

    /** \u9a8c\u8bc1<code>(Object) ThreadContext.get(testKey)</code>\u4e3a\u7a7a\u3002 */
    @Test
    void threadContext_set_shouldIgnoreNullString() {
        ThreadContext.set(testKey, "");

        assertThat((Object) ThreadContext.get(testKey)).isNull();
    }

    /** \u9a8c\u8bc1<code>(Object) ThreadContext.get(testKey)</code>\u4e3a\u7a7a\uff0c<code>(String) ThreadContext.get(testKey)</code> \u7b49\u4e8e <code>"v1"</code>\u3002 */
    @Test
    void threadContext_setConditional_shouldStoreOnlyWhenTrue() {
        ThreadContext.set(false, testKey, "v1");
        assertThat((Object) ThreadContext.get(testKey)).isNull();

        ThreadContext.set(true, testKey, "v1");
        assertThat((String) ThreadContext.get(testKey)).isEqualTo("v1");
    }

    /** \u9a8c\u8bc1<code>(Object) ThreadContext.get(testKey)</code>\u4e3a\u7a7a\u3002 */
    @Test
    void threadContext_remove_shouldDeleteEntry() {
        ThreadContext.set(testKey, "value");
        ThreadContext.remove(testKey);

        assertThat((Object) ThreadContext.get(testKey)).isNull();
    }

    /** \u9a8c\u8bc1<code>(Object) ThreadContext.get(testKey)</code>\u4e3a\u7a7a\u3002 */
    @Test
    void threadContext_clear_shouldRemoveThreadBindings() {
        ThreadContext.set(testKey, "value");
        ThreadContext.clear();

        assertThat((Object) ThreadContext.get(testKey)).isNull();
    }

    // ========================= Contexts =========================

    /** \u9a8c\u8bc1<code>resolved</code> \u5305\u542b <code>publisher</code>\u3002 */
    @Test
    void contexts_inject_shouldFallbackToBaseContext() {
        DomainEventPublisher publisher = stub("base");
        Contexts.register(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, publisher);

        Optional<DomainEventPublisher> resolved = Contexts.get(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class);

        assertThat(resolved).contains(publisher);
    }

    /** \u9a8c\u8bc1<code>Contexts.getOrThrow(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class)</code> \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
    @Test
    void contexts_getOrThrow_shouldThrowWhenNotFound() {
        BaseContext.remove(SpiKeys.DOMAIN_EVENT_PUBLISHER);
        ThreadContext.clear();

        assertThatThrownBy(() -> Contexts.getOrThrow(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPI service not found");
    }

    /** \u9a8c\u8bc1<code>resolved</code> \u5305\u542b <code>thread</code>\u3002 */
    @Test
    void contexts_inject_threadShouldOverrideBase() {
        DomainEventPublisher base = stub("base");
        DomainEventPublisher thread = stub("thread");
        Contexts.register(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, base);
        ThreadContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, thread);

        Optional<DomainEventPublisher> resolved = Contexts.get(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class);

        assertThat(resolved).contains(thread);
    }

    /** \u9a8c\u8bc1<code>BaseContext.get(testKey, DomainEventPublisher.class)</code> \u5305\u542b <code>publisher</code>\u3002 */
    @Test
    void contexts_register_shouldDelegateToBaseContext() {
        DomainEventPublisher publisher = stub("registered");
        Contexts.register(testKey, DomainEventPublisher.class, publisher);

        assertThat(BaseContext.get(testKey, DomainEventPublisher.class)).contains(publisher);
    }
}
