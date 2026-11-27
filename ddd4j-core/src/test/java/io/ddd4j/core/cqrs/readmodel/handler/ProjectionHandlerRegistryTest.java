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
package io.ddd4j.core.cqrs.readmodel.handler;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.StringEntityId;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ProjectionHandlerRegistry} 注册与路由契约测试。
 * <p>
 * 重点守护：register 的 <b>整批拒绝</b>（all-or-nothing）语义——多类型 handler
 * 任一事件类型冲突时，全部类型均不落库，不产生半注册状态；未注册类型的
 * {@code findHandler} 返回 {@link Optional#empty()}（兜底策略归
 * {@link ProjectionDispatcher}）。语义与 ddd4j-core 中 CommandRegistry 的
 * {@code CommandRegistry} 同源（ADR-0004）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
class ProjectionHandlerRegistryTest {

    /**
     * 执行 <code>set Of</code>，调用 <code>Collections.unmodifiableSet</code>、<code>Arrays.asList</code>。
     *
     * @param eventTypes 传入此操作的 <code>Class&lt;? extends DomainEvent&lt;?&gt;&gt;[]</code> 类型 <code>eventTypes</code>
     * @return 返回表达式 <code>Collections.unmodifiableSet(new LinkedHashSet&lt;&gt;(Arrays.asList(eventTypes)))</code>
     */
    @SafeVarargs
    private static Set<Class<? extends DomainEvent<?>>> setOf(
            Class<? extends DomainEvent<?>>... eventTypes) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(eventTypes)));
    }

    /** \u5f53\u524d\u7528\u4f8b\u6ce8\u518c\u88ab\u6d4b\u7ec4\u4ef6\u548c\u89c2\u5bdf\u6307\u6807\u7684\u5bb9\u5668\u3002 */
    private final ProjectionHandlerRegistry registry = new ProjectionHandlerRegistry();

    /** \u9a8c\u8bc1<code>registry.findHandler(OrderCreated.class).orElse(null)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>handler</code>\uff0c<code>registry.findHandler(OrderPaid.class).orElse(null)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>handler</code>\u3002 */
    @Test
    void registeredHandlerIsFoundByExactEventType() {
        RecordingHandler handler = new RecordingHandler("order-summary",
                setOf(OrderCreated.class, OrderPaid.class));

        registry.register(handler);

        assertSame(handler, registry.findHandler(OrderCreated.class).orElse(null),
                "findHandler should return the registered handler for the first subscribed type");
        assertSame(handler, registry.findHandler(OrderPaid.class).orElse(null),
                "findHandler should return the registered handler for the second subscribed type");
    }

    /** \u9a8c\u8bc1<code>found.isPresent()</code>\u4e3a\u5047\u3002 */
    @Test
    void findHandlerReturnsEmptyForUnregisteredEventType() {
        registry.register(new RecordingHandler("order-summary", setOf(OrderCreated.class)));

        Optional<ProjectionHandler> found = registry.findHandler(OrderPaid.class);

        assertFalse(found.isPresent(),
                "unregistered event type should yield Optional.empty() (dispatcher owns the fallback strategy)");
    }

    /** \u9a8c\u8bc1<code>registry.findHandler(null)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void findHandlerRejectsNullEventType() {
        assertThrows(NullPointerException.class, () -> registry.findHandler(null));
    }

    /** \u9a8c\u8bc1<code>registry.register(null)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void registerRejectsNullHandler() {
        assertThrows(NullPointerException.class, () -> registry.register(null));
    }

    /** \u9a8c\u8bc1<code>registry.register(new RecordingHandler("invalid", eventTypes))</code> \u629b\u51fa <code>NullPointerException.class</code>\uff0c<code>ex</code> \u7684\u5f02\u5e38\u6d88\u606f\u4e3a <code>"event type must not be null"</code>\uff0c<code>registry.findHandler(OrderPaid.class).isPresent()</code>\u4e3a\u5047\u3002 */
    @Test
    void registerRejectsNullEventTypeBeforeRegisteringAnyType() {
        Set<Class<? extends DomainEvent<?>>> eventTypes = new LinkedHashSet<>();
        eventTypes.add(OrderPaid.class);
        eventTypes.add(null);

        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> registry.register(new RecordingHandler("invalid", eventTypes)));

        assertThat(ex).hasMessage("event type must not be null");
        assertFalse(registry.findHandler(OrderPaid.class).isPresent());
    }

    /** 验证<code>registry.register(new RecordingHandler("another-view", setOf(OrderCreated.class)))</code> 抛出 <code>IllegalStateException.class</code>，<code>ex</code> 的异常消息包含 <code>OrderCreated.class.getName()</code>。 */
    @Test
    void duplicateEventTypeRegistrationThrowsIllegalStateWithEventName() {
        registry.register(new RecordingHandler("order-summary", setOf(OrderCreated.class)));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.register(new RecordingHandler("another-view", setOf(OrderCreated.class))));

        assertThat(ex).hasMessageContaining(OrderCreated.class.getName());
    }

    /** \u9a8c\u8bc1<code>registry.register(multiTypeHandler)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>ex</code> \u7684\u5f02\u5e38\u6d88\u606f\u5305\u542b <code>"order-summary"</code>\uff0c<code>registry.all()</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\u3002 */
    @Test
    void conflictingBatchRegistrationIsRejectedEntirely() {
        registry.register(new RecordingHandler("order-summary", setOf(OrderCreated.class)));
        RecordingHandler multiTypeHandler = new RecordingHandler("order-lifecycle",
                setOf(OrderCreated.class, OrderPaid.class));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.register(multiTypeHandler));

        assertThat(ex).hasMessageContaining(OrderCreated.class.getName());
        assertFalse(registry.findHandler(OrderPaid.class).isPresent(),
                "non-conflicting types of the rejected handler must NOT be registered (all-or-nothing)");
        assertThat(registry.all())
                .hasSize(1)
                .doesNotContain(multiTypeHandler);
    }

    /** \u9a8c\u8bc1<code>registry.all().add(new RecordingHandler("another-view", Set.of(OrderPaid.class)))</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\u3002 */
    @Test
    void allViewIsImmutable() {
        registry.register(new RecordingHandler("order-summary", setOf(OrderCreated.class)));

        assertThrows(UnsupportedOperationException.class,
                () -> registry.all().add(new RecordingHandler("another-view", setOf(OrderPaid.class))));
    }

    /** \u9a8c\u8bc1<code>registry.findHandler(OrderCreated.class).orElse(null)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>summaryHandler</code>\uff0c<code>registry.findHandler(OrderPaid.class).orElse(null)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>paymentHandler</code>\uff0c<code>registry.all()</code> \u7684\u5927\u5c0f\u4e3a <code>3</code>\u3002 */
    @Test
    void multipleHandlersRouteToTheirOwnEventTypesAndMultiTypeHandlerAppearsOnce() {
        RecordingHandler summaryHandler = new RecordingHandler("order-summary", setOf(OrderCreated.class));
        RecordingHandler paymentHandler = new RecordingHandler("payment-view", setOf(OrderPaid.class));
        RecordingHandler lifecycleHandler = new RecordingHandler("order-lifecycle",
                setOf(OrderShipped.class, OrderCancelled.class));

        registry.register(summaryHandler);
        registry.register(paymentHandler);
        registry.register(lifecycleHandler);

        assertSame(summaryHandler, registry.findHandler(OrderCreated.class).orElse(null));
        assertSame(paymentHandler, registry.findHandler(OrderPaid.class).orElse(null));
        assertSame(lifecycleHandler, registry.findHandler(OrderShipped.class).orElse(null));
        assertSame(lifecycleHandler, registry.findHandler(OrderCancelled.class).orElse(null));
        assertThat(registry.all())
                .hasSize(3)
                .containsOnlyOnce(lifecycleHandler);
    }

    /** \u9a8c\u8bc1<code>handler.getName()</code> \u7b49\u4e8e <code>"order-summary"</code>\uff0c<code>handler.getCron()</code> \u7b49\u4e8e <code>"0/5 * * * * *"</code>\uff0c<code>handler.getChunkSize()</code> \u7b49\u4e8e <code>100</code>\u3002 */
    @Test
    void defaultHandlerConfigurationMatchesContract() {
        ProjectionHandler handler = new RecordingHandler("order-summary", setOf(OrderCreated.class));

        assertThat(handler.getName()).isEqualTo("order-summary");
        assertThat(handler.getCron()).isEqualTo("0/5 * * * * *");
        assertThat(handler.getChunkSize()).isEqualTo(100);
    }

    /**
     * 记录型 handler 测试替身（可注入失败）。
     */
    static final class RecordingHandler implements ProjectionHandler {

        /** \u5f53\u524d\u6d4b\u8bd5\u6a21\u578b\u7528\u4e8e\u5c55\u793a\u548c\u65ad\u8a00\u7684\u540d\u79f0\u3002 */
        private final String name;

        /** \u5939\u5177\u5411\u6295\u5f71\u8fd0\u884c\u5668\u58f0\u660e\u8ba2\u9605\u7684\u4e8b\u4ef6\u7c7b\u578b\u3002 */
        private final Set<Class<? extends DomainEvent<?>>> eventTypes;

        /** \u5f53\u524d\u6d4b\u8bd5\u4e2d\u5df2\u7ecf\u4ea4\u7ed9\u5904\u7406\u5668\u7684\u8bb0\u5f55\u3002 */
        private final List<DomainEvent<?>> handled = new java.util.ArrayList<>();

        /** \u5f53\u524d\u5931\u8d25\u8def\u5f84\u6240\u7528\u6216\u89c2\u5bdf\u5230\u7684\u539f\u59cb\u5f02\u5e38\u3002 */
        private RuntimeException failure;

        /**
         * \u8bbe\u7f6e <code>name</code>\u3001<code>eventTypes</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>RecordingHandler</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
         * @param eventTypes \u7528\u4e8e\u521d\u59cb\u5316 <code>RecordingHandler</code> \u7684 <code>Set&lt;Class&lt;? extends DomainEvent&lt;?&gt;&gt;&gt;</code> \u503c
         */
        RecordingHandler(String name, Set<Class<? extends DomainEvent<?>>> eventTypes) {
            this.name = name;
            this.eventTypes = eventTypes;
        }

        /**
         * \u8bbe\u7f6e<code>failure</code> \u5b57\u6bb5\u3002
         * @param failure \u8981\u5199\u5165\u7684\u503c
         */
        void failWith(RuntimeException failure) {
            this.failure = failure;
        }

        /**
         * \u8bfb\u53d6\u5f53\u524d\u6d4b\u8bd5\u4e2d\u5df2\u7ecf\u4ea4\u7ed9\u5904\u7406\u5668\u7684\u8bb0\u5f55\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4e2d\u5df2\u7ecf\u4ea4\u7ed9\u5904\u7406\u5668\u7684\u8bb0\u5f55
         */
        List<DomainEvent<?>> handled() {
            return handled;
        }

        /**
         * 获取值。
         * @return 结果字符串。
         */
        @Override
        public String getName() {
            return name;
        }

        /**
         * 返回此处理器接受的事件类型。
         *
         * @return 实际返回<code>eventTypes</code>，供调用方取得当前值
         */
        @Override
        public Collection<Class<? extends DomainEvent<?>>> eventTypes() {
            return eventTypes;
        }

        /**
         * 处理请求。
         * @param event 事件消息。
         */
        @Override
        public void handle(DomainEvent<?> event) {
            if (failure != null) {
                throw failure;
            }
            handled.add(event);
        }
    }

    /**
     * 类型说明。
     * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
     * @since 3.0.x
     */
    static final class OrderCreated extends DomainEvent<StringEntityId> {

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderCreated</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        OrderCreated() {
            super("order-1");
        }
    }

    /**
     * 类型说明。
     * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
     * @since 3.0.x
     */
    static final class OrderPaid extends DomainEvent<StringEntityId> {

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderPaid</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        OrderPaid() {
            super("order-1");
        }
    }

    /**
     * 类型说明。
     * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
     * @since 3.0.x
     */
    static final class OrderShipped extends DomainEvent<StringEntityId> {

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderShipped</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        OrderShipped() {
            super("order-1");
        }
    }

    /**
     * 类型说明。
     * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
     * @since 3.0.x
     */
    static final class OrderCancelled extends DomainEvent<StringEntityId> {

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>OrderCancelled</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        OrderCancelled() {
            super("order-1");
        }
    }
}
