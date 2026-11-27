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

    /**
     * 供不同用例定位同一项测试设置的键。
     */
    private String testKey;

    /**
     * 测试执行前执行 <code>UUID.randomUUID()</code>、<code>BaseContext.clear()</code>、<code>ThreadContext.clear()</code> 完成资源准备。
     */
    @BeforeEach
    void setUp() {
        testKey = "ddd4j.test." + UUID.randomUUID();
        BaseContext.clear();
        ThreadContext.clear();
    }

    /**
     * 测试结束后执行 <code>BaseContext.clear()</code>、<code>ThreadContext.clear()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void tearDown() {
        BaseContext.clear();
        ThreadContext.clear();
    }

    /**
     * 构造 <code>DomainEventPublisher</code> 作为当前测试的输入或结果。
     *
     * @param name 当前测试指定的对象或属性名称
     * @return 提供给后续契约断言的 <code>DomainEventPublisher</code> 结果
     */
    private DomainEventPublisher stub(String name) {
        return new DomainEventPublisher() {
            @Override
            public <ID extends EntityId> void publish(DomainEvent<ID> event) {

            }

            @Override
            public void publish(Object event) {
            }

            @Override
            public <ID extends EntityId> void publishAll(Collection<DomainEvent<ID>> domainEvents) {
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

    // ========================= BaseContext =========================

    /**
     * 验证<code>resolved</code> 包含 <code>publisher</code>。
     */
    @Test
    void baseContext_injectTyped_shouldLookupBySameType() {
        DomainEventPublisher publisher = stub("p1");
        BaseContext.inject(testKey, DomainEventPublisher.class, publisher);

        Optional<DomainEventPublisher> resolved = BaseContext.get(testKey, DomainEventPublisher.class);

        assertThat(resolved).contains(publisher);
    }

    /**
     * 验证<code>BaseContext.inject(testKey, DomainEventPublisher.class, null)</code> 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void baseContext_injectTyped_shouldRejectNullValue() {
        assertThatThrownBy(() -> BaseContext.inject(testKey, DomainEventPublisher.class, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 验证<code>BaseContext.inject(null, DomainEventPublisher.class, stub("p"))</code> 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void baseContext_injectTyped_shouldRejectNullKey() {
        assertThatThrownBy(() -> BaseContext.inject(null, DomainEventPublisher.class, stub("p")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 验证<code>BaseContext.inject(testKey, targetType, "not-a-publisher")</code> 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void baseContext_injectTyped_shouldRejectTypeMismatch() {
        // Use a raw-typed reference to defeat generic inference so the runtime isInstance check fires.
        Class targetType = DomainEventPublisher.class;
        assertThatThrownBy(() -> BaseContext.inject(testKey, targetType, "not-a-publisher"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 验证<code>BaseContext.get(testKey, Integer.class)</code>为空。
     */
    @Test
    void baseContext_injectTyped_shouldReturnEmptyForTypeMismatch() {
        BaseContext.inject(testKey, "value");

        assertThat(BaseContext.get(testKey, Integer.class)).isEmpty();
    }

    /**
     * 验证<code>(String) BaseContext.get(testKey)</code> 等于 <code>"value"</code>，<code>BaseContext.contains(testKey)</code>为真。
     */
    @Test
    void baseContext_injectUntyped_shouldStoreAndRetrieveValue() {
        BaseContext.inject(testKey, "value");

        assertThat((String) BaseContext.get(testKey)).isEqualTo("value");
        assertThat(BaseContext.contains(testKey)).isTrue();
    }

    /**
     * 验证<code>(Object) BaseContext.get("missing")</code>为空。
     */
    @Test
    void baseContext_get_shouldReturnNullForMissingKey() {
        assertThat((Object) BaseContext.get("missing")).isNull();
    }

    /**
     * 验证<code>BaseContext.get("missing", "default")</code> 等于 <code>"default"</code>。
     */
    @Test
    void baseContext_getWithDefault_shouldReturnDefaultForMissingKey() {
        assertThat(BaseContext.get("missing", "default")).isEqualTo("default");
    }

    /**
     * 验证<code>BaseContext.contains(null)</code>为假。
     */
    @Test
    void baseContext_contains_shouldReturnFalseForNullKey() {
        assertThat(BaseContext.contains(null)).isFalse();
    }

    /**
     * 验证<code>BaseContext.contains(testKey)</code>为真，<code>BaseContext.contains(testKey)</code>为假。
     */
    @Test
    void baseContext_remove_shouldDeleteEntry() {
        BaseContext.inject(testKey, "value");
        assertThat(BaseContext.contains(testKey)).isTrue();

        BaseContext.remove(testKey);

        assertThat(BaseContext.contains(testKey)).isFalse();
    }

    /**
     * 验证<code>BaseContext.contains(testKey)</code>为假。
     */
    @Test
    void baseContext_clear_shouldRemoveAllEntries() {
        BaseContext.inject(testKey, "v1");
        BaseContext.inject(testKey + "2", "v2");

        BaseContext.clear();

        assertThat(BaseContext.contains(testKey)).isFalse();
    }

    // ========================= ThreadContext =========================

    /**
     * 验证<code>resolved</code> 包含 <code>publisher</code>。
     */
    @Test
    void threadContext_injectTyped_shouldLookupOnSameThread() {
        DomainEventPublisher publisher = stub("tp");
        ThreadContext.inject(testKey, DomainEventPublisher.class, publisher);

        Optional<DomainEventPublisher> resolved = ThreadContext.get(testKey, DomainEventPublisher.class);

        assertThat(resolved).contains(publisher);
    }

    /**
     * 验证<code>ThreadContext.get(testKey, Integer.class)</code>为空。
     */
    @Test
    void threadContext_injectTyped_shouldReturnEmptyForTypeMismatch() {
        ThreadContext.inject(testKey, String.class, "value");

        assertThat(ThreadContext.get(testKey, Integer.class)).isEmpty();
    }

    /**
     * 验证<code>ThreadContext.get(testKey, String.class)</code>为空。
     */
    @Test
    void threadContext_injectTyped_shouldReturnEmptyForMissing() {
        assertThat(ThreadContext.get(testKey, String.class)).isEmpty();
    }

    /**
     * 验证<code>(String) ThreadContext.get(testKey)</code> 等于 <code>"value"</code>。
     */
    @Test
    void threadContext_set_shouldStoreValue() {
        ThreadContext.set(testKey, "value");

        assertThat((String) ThreadContext.get(testKey)).isEqualTo("value");
    }

    /**
     * 验证<code>(Object) ThreadContext.get(testKey)</code>为空。
     */
    @Test
    void threadContext_set_shouldIgnoreNullString() {
        ThreadContext.set(testKey, "");

        assertThat((Object) ThreadContext.get(testKey)).isNull();
    }

    /**
     * 验证<code>(Object) ThreadContext.get(testKey)</code>为空，<code>(String) ThreadContext.get(testKey)</code> 等于 <code>"v1"</code>。
     */
    @Test
    void threadContext_setConditional_shouldStoreOnlyWhenTrue() {
        ThreadContext.set(false, testKey, "v1");
        assertThat((Object) ThreadContext.get(testKey)).isNull();

        ThreadContext.set(true, testKey, "v1");
        assertThat((String) ThreadContext.get(testKey)).isEqualTo("v1");
    }

    /**
     * 验证<code>(Object) ThreadContext.get(testKey)</code>为空。
     */
    @Test
    void threadContext_remove_shouldDeleteEntry() {
        ThreadContext.set(testKey, "value");
        ThreadContext.remove(testKey);

        assertThat((Object) ThreadContext.get(testKey)).isNull();
    }

    /**
     * 验证<code>(Object) ThreadContext.get(testKey)</code>为空。
     */
    @Test
    void threadContext_clear_shouldRemoveThreadBindings() {
        ThreadContext.set(testKey, "value");
        ThreadContext.clear();

        assertThat((Object) ThreadContext.get(testKey)).isNull();
    }

    // ========================= Contexts =========================

    /**
     * 验证<code>resolved</code> 包含 <code>publisher</code>。
     */
    @Test
    void contexts_inject_shouldFallbackToBaseContext() {
        DomainEventPublisher publisher = stub("base");
        Contexts.register(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, publisher);

        Optional<DomainEventPublisher> resolved = Contexts.get(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class);

        assertThat(resolved).contains(publisher);
    }

    /**
     * 验证<code>Contexts.getOrThrow(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class)</code> 抛出 <code>IllegalStateException.class</code>。
     */
    @Test
    void contexts_getOrThrow_shouldThrowWhenNotFound() {
        BaseContext.remove(SpiKeys.DOMAIN_EVENT_PUBLISHER);
        ThreadContext.clear();

        assertThatThrownBy(() -> Contexts.getOrThrow(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPI service not found");
    }

    /**
     * 验证<code>resolved</code> 包含 <code>thread</code>。
     */
    @Test
    void contexts_inject_threadShouldOverrideBase() {
        DomainEventPublisher base = stub("base");
        DomainEventPublisher thread = stub("thread");
        Contexts.register(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, base);
        ThreadContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, thread);

        Optional<DomainEventPublisher> resolved = Contexts.get(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class);

        assertThat(resolved).contains(thread);
    }

    /**
     * 验证<code>BaseContext.get(testKey, DomainEventPublisher.class)</code> 包含 <code>publisher</code>。
     */
    @Test
    void contexts_register_shouldDelegateToBaseContext() {
        DomainEventPublisher publisher = stub("registered");
        Contexts.register(testKey, DomainEventPublisher.class, publisher);

        assertThat(BaseContext.get(testKey, DomainEventPublisher.class)).contains(publisher);
    }
}
