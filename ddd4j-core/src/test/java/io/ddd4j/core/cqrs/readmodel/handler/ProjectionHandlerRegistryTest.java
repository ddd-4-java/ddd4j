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

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.StringEntityId;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ProjectionHandlerRegistry} 注册与路由契约测试。
 * <p>
 * 守护同名视图整批拒绝、多视图订阅和旧单值查询确定性；与命令单处理器规则独立。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
class ProjectionHandlerRegistryTest {

    /**
     * 当前用例注册被测组件和观察指标的容器。
     */
    private final ProjectionHandlerRegistry registry = new ProjectionHandlerRegistry();

    /**
     * 验证<code>registry.findHandler(OrderCreated.class).orElse(null)</code> 与…为同一实例 <code>handler</code>，<code>registry.findHandler(OrderPaid.class).orElse(null)</code> 与…为同一实例 <code>handler</code>。
     */
    @Test
    void registeredHandlerIsFoundByExactEventType() {
        RecordingHandler handler = new RecordingHandler("order-summary",
                new java.util.LinkedHashSet<>(java.util.Arrays.asList(OrderCreated.class, OrderPaid.class)));

        registry.register(handler);

        assertSame(handler, registry.findHandler(OrderCreated.class).orElse(null),
                "findHandler should return the registered handler for the first subscribed type");
        assertSame(handler, registry.findHandler(OrderPaid.class).orElse(null),
                "findHandler should return the registered handler for the second subscribed type");
    }

    /**
     * 验证<code>found.isPresent()</code>为假。
     */
    @Test
    void findHandlerReturnsEmptyForUnregisteredEventType() {
        registry.register(new RecordingHandler("order-summary", java.util.Collections.singleton(OrderCreated.class)));

        Optional<ProjectionHandler> found = registry.findHandler(OrderPaid.class);

        assertFalse(found.isPresent(),
                "unregistered event type should yield Optional.empty() (dispatcher owns the fallback strategy)");
    }

    /**
     * 验证<code>registry.findHandler(null)</code> 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void findHandlerRejectsNullEventType() {
        assertThrows(NullPointerException.class, () -> registry.findHandler(null));
    }

    /**
     * 验证<code>registry.register(null)</code> 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void registerRejectsNullHandler() {
        assertThrows(NullPointerException.class, () -> registry.register(null));
    }

    /**
     * 验证<code>registry.register(new RecordingHandler("invalid", eventTypes))</code> 抛出 <code>NullPointerException.class</code>，<code>ex</code> 的异常消息为 <code>"event type must not be null"</code>，<code>registry.findHandler(OrderPaid.class).isPresent()</code>为假。
     */
    @Test
    void registerRejectsNullEventTypeBeforeRegisteringAnyType() {
        Set<Class<? extends DomainEvent<?>>> eventTypes = new LinkedHashSet<>();
        eventTypes.add(OrderPaid.class);
        eventTypes.add(null);

        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> registry.register(new RecordingHandler("invalid", eventTypes)));

        assertThat(ex).hasMessage("event type must not be null");
        assertThat(registry.all()).isEmpty();
        assertFalse(registry.findHandler(OrderPaid.class).isPresent());
    }

    /**
     * 验证<code>registry.register(new RecordingHandler("order-summary", java.util.Collections.singleton(OrderCreated.class)))</code> 抛出 <code>IllegalStateException.class</code>，<code>ex</code> 的异常消息包含 <code>"order-summary"</code>。
     */
    @Test
    void duplicateViewRegistrationThrowsIllegalStateWithViewName() {
        registry.register(new RecordingHandler("order-summary", java.util.Collections.singleton(OrderCreated.class)));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.register(new RecordingHandler("order-summary", java.util.Collections.singleton(OrderCreated.class))));

        assertThat(ex).hasMessageContaining("order-summary");
    }

    /**
     * 验证<code>registry.register(multiTypeHandler)</code> 抛出 <code>IllegalStateException.class</code>，<code>ex</code> 的异常消息包含 <code>"order-summary"</code>，<code>registry.all()</code> 的大小为 <code>1</code>。
     */
    @Test
    void conflictingBatchRegistrationIsRejectedEntirely() {
        registry.register(new RecordingHandler("order-summary", java.util.Collections.singleton(OrderCreated.class)));
        RecordingHandler multiTypeHandler = new RecordingHandler("order-summary",
                new java.util.LinkedHashSet<>(java.util.Arrays.asList(OrderCreated.class, OrderPaid.class)));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.register(multiTypeHandler));

        assertThat(ex).hasMessageContaining("order-summary");
        assertFalse(registry.findHandler(OrderPaid.class).isPresent(),
                "non-conflicting types of the rejected handler must NOT be registered (all-or-nothing)");
        assertThat(registry.all())
                .hasSize(1)
                .doesNotContain(multiTypeHandler);
    }

    /**
     * 验证<code>registry.all()</code> 按顺序仅包含 <code>first</code>、<code>second</code>，<code>registry.findHandlers(OrderCreated.class)</code> 按顺序仅包含 <code>first</code>、<code>second</code>，<code>registry.findHandler(OrderCreated.class).orElseThrow(java.util.NoSuchElementException::new)</code> 与…为同一实例 <code>first</code>。
     */
    @Test
    void independentViewsCanSubscribeToSameEventAndLegacyLookupUsesFirst() {
        RecordingHandler first = new RecordingHandler("first", java.util.Collections.singleton(OrderCreated.class));
        RecordingHandler second = new RecordingHandler("second", java.util.Collections.singleton(OrderCreated.class));
        registry.register(first);
        registry.register(second);
        assertThat(registry.all()).containsExactly(first, second);
        assertThat(registry.findHandlers(OrderCreated.class)).containsExactly(first, second);
        assertThrows(UnsupportedOperationException.class,
                () -> registry.findHandlers(OrderCreated.class).clear());
        assertSame(first, registry.findHandler(OrderCreated.class).orElseThrow(java.util.NoSuchElementException::new));
    }

    /**
     * 验证<code>registry.all().add(new RecordingHandler("another-view", java.util.Collections.singleton(OrderPaid.class)))</code> 抛出 <code>UnsupportedOperationException.class</code>。
     */
    @Test
    void allViewIsImmutable() {
        registry.register(new RecordingHandler("order-summary", java.util.Collections.singleton(OrderCreated.class)));

        assertThrows(UnsupportedOperationException.class,
                () -> registry.all().add(new RecordingHandler("another-view", java.util.Collections.singleton(OrderPaid.class))));
    }

    /**
     * 验证<code>registry.findHandler(OrderCreated.class).orElse(null)</code> 与…为同一实例 <code>summaryHandler</code>，<code>registry.findHandler(OrderPaid.class).orElse(null)</code> 与…为同一实例 <code>paymentHandler</code>，<code>registry.all()</code> 的大小为 <code>3</code>。
     */
    @Test
    void multipleHandlersRouteToTheirOwnEventTypesAndMultiTypeHandlerAppearsOnce() {
        RecordingHandler summaryHandler = new RecordingHandler("order-summary", java.util.Collections.singleton(OrderCreated.class));
        RecordingHandler paymentHandler = new RecordingHandler("payment-view", java.util.Collections.singleton(OrderPaid.class));
        RecordingHandler lifecycleHandler = new RecordingHandler("order-lifecycle",
                new java.util.LinkedHashSet<>(java.util.Arrays.asList(OrderShipped.class, OrderCancelled.class)));

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

    /**
     * 验证<code>handler.getName()</code> 等于 <code>"order-summary"</code>，<code>handler.getCron()</code> 等于 <code>"0/5 * * * * *"</code>，<code>handler.getChunkSize()</code> 等于 <code>100</code>。
     */
    @Test
    void defaultHandlerConfigurationMatchesContract() {
        ProjectionHandler handler = new RecordingHandler("order-summary", java.util.Collections.singleton(OrderCreated.class));

        assertThat(handler.getName()).isEqualTo("order-summary");
        assertThat(handler.getCron()).isEqualTo("0/5 * * * * *");
        assertThat(handler.getChunkSize()).isEqualTo(100);
    }

    /**
     * 验证<code>registry.findHandler(OrderCreated.class).orElseThrow(java.util.NoSuchElementException::new)</code> 与…为同一实例 <code>handler</code>，<code>registry.findHandlers(OrderCreated.class)</code> 按顺序仅包含 <code>handler</code>，<code>registry.all()</code> 按顺序仅包含 <code>handler</code>。
     */
    @Test
    void mutableHandlerDoesNotChangeRegisteredSubscriptionsOrLegacyIdentity() {
        MutableHandler handler = new MutableHandler();
        registry.register(handler);
        handler.types.clear();
        handler.types.add(OrderPaid.class);
        handler.name = "second";
        assertSame(handler, registry.findHandler(OrderCreated.class).orElseThrow(java.util.NoSuchElementException::new));
        assertThat(registry.findHandlers(OrderCreated.class)).containsExactly(handler);
        assertThat(registry.findHandlers(OrderPaid.class)).isEmpty();
        assertThat(registry.all()).containsExactly(handler);
    }

    /**
     * 可变业务配置替身，用于验证登记快照与执行入口的一致性。
     */
    static final class MutableHandler implements ProjectionHandler {
        /**
         * 用于验证注册查找和泛型路由的候选类型。
         */
        final Set<Class<? extends DomainEvent<?>>> types = new LinkedHashSet<>(java.util.Collections.singleton(OrderCreated.class));
        /**
         * 当前测试中已经交给处理器的记录。
         */
        final List<DomainEvent<?>> handled = new java.util.ArrayList<>();
        /**
         * 当前测试模型用于展示和断言的名称。
         */
        String name = "first";

        /**
         * 返回当前配置、视图或处理器的名称。
         *
         * @return 实际返回<code>name</code>，供调用方取得当前值
         */
        @Override
        public String getName() {
            return name;
        }

        /**
         * 返回此处理器接受的事件类型。
         *
         * @return 实际返回<code>types</code>，供调用方取得当前值
         */
        @Override
        public Collection<Class<? extends DomainEvent<?>>> eventTypes() {
            return types;
        }

        /**
         * 处理调用方传入的消息或请求。
         *
         * <p>测试夹具会调用 <code>handled.add(event)</code>。</p>
         *
         * @param event 订阅类型之一的领域事件，非空
         */
        @Override
        public void handle(DomainEvent<?> event) {
            handled.add(event);
        }
    }

    /**
     * 记录型 handler 测试替身（可注入失败）。
     */
    static final class RecordingHandler implements ProjectionHandler {

        /**
         * 当前测试模型用于展示和断言的名称。
         */
        private final String name;

        /**
         * 夹具向投影运行器声明订阅的事件类型。
         */
        private final Set<Class<? extends DomainEvent<?>>> eventTypes;

        /**
         * 当前测试中已经交给处理器的记录。
         */
        private final List<DomainEvent<?>> handled = new java.util.ArrayList<>();

        /**
         * 当前失败路径所用或观察到的原始异常。
         */
        private RuntimeException failure;

        /**
         * 设置 <code>name</code>、<code>eventTypes</code>，用于构造 <code>RecordingHandler</code> 测试实例。
         *
         * @param name       当前测试指定的对象或属性名称
         * @param eventTypes 用于初始化 <code>RecordingHandler</code> 的 <code>Set&lt;Class&lt;? extends DomainEvent&lt;?&gt;&gt;&gt;</code> 值
         */
        RecordingHandler(String name, Set<Class<? extends DomainEvent<?>>> eventTypes) {
            this.name = name;
            this.eventTypes = eventTypes;
        }

        /**
         * 设置<code>failure</code> 字段。
         *
         * @param failure 要写入的值
         */
        void failWith(RuntimeException failure) {
            this.failure = failure;
        }

        /**
         * 读取当前测试中已经交给处理器的记录。
         *
         * @return 当前测试中已经交给处理器的记录
         */
        List<DomainEvent<?>> handled() {
            return handled;
        }

        /**
         * 返回当前配置、视图或处理器的名称。
         *
         * @return 实际返回<code>name</code>，供调用方取得当前值
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
         * 处理调用方传入的消息或请求。
         *
         * <p>测试夹具会调用 <code>handled.add(event)</code>。</p>
         *
         * @param event 订阅类型之一的领域事件，非空
         */
        @Override
        public void handle(DomainEvent<?> event) {
            if (Objects.nonNull(failure)) {
                throw failure;
            }
            handled.add(event);
        }
    }

    /**
     * 表示订单创建的测试事件。
     */
    static final class OrderCreated extends DomainEvent<StringEntityId> {

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderCreated</code> 测试夹具。
         */
        OrderCreated() {
            super("order-1");
        }
    }

    /**
     * 表示订单完成付款的测试事件。
     */
    static final class OrderPaid extends DomainEvent<StringEntityId> {

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderPaid</code> 测试夹具。
         */
        OrderPaid() {
            super("order-1");
        }
    }

    /**
     * 表示订单已经发运的测试事件。
     */
    static final class OrderShipped extends DomainEvent<StringEntityId> {

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderShipped</code> 测试夹具。
         */
        OrderShipped() {
            super("order-1");
        }
    }

    /**
     * 供传输、序列化或处理器分发验证的领域事件样本。
     */
    static final class OrderCancelled extends DomainEvent<StringEntityId> {

        /**
         * 使用 <code>super()</code> 初始化 <code>OrderCancelled</code> 测试夹具。
         */
        OrderCancelled() {
            super("order-1");
        }
    }
}
