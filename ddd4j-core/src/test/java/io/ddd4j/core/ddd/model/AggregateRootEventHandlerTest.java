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

package io.ddd4j.core.ddd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.ddd4j.core.ddd.event.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AggregateRoot} 事件处理器机制的全覆盖测试：{@code @EventHandler}
 * 派发频次、{@code ignoreOnReplay} 语义、处理器缓存复用、private 处理器可达性
 * 与派发失败不入队。
 *
 * <p>与 {@link AggregateRootApplyTest} 互补：那边验证 apply/loadFromHistory 的
 * 基本契约，这里按 Counter/IncrementEvent 场景逐一覆盖处理器机制本身。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
class AggregateRootEventHandlerTest {

    /**
     * 验证<code>counter.count</code> 等于 <code>1</code>。
     */
    @Test
    void eventHandlerInvokedOnce() {
        Counter counter = new Counter();

        counter.trigger();

        assertEquals(1, counter.count, "apply should dispatch the event to @EventHandler exactly once");
    }

    /**
     * 验证<code>counter.count</code> 等于 <code>3</code>。
     */
    @Test
    void eventHandlerInvokedForEachTrigger() {
        Counter counter = new Counter();

        counter.trigger();
        counter.trigger();
        counter.trigger();

        assertEquals(3, counter.count, "each apply should dispatch once");
    }

    /**
     * 验证<code>counter.count</code> 等于 <code>1</code>，<code>counter.sideEffectRan</code>为假。
     */
    @Test
    void ignoreOnReplayHandlerNotInvokedOnLoad() {
        Counter counter = new Counter();

        counter.loadFromHistory(Arrays.asList(new IncrementEvent(), new CountNotifiedEvent()));

        assertEquals(1, counter.count, "normal handler should replay and rebuild state");
        assertFalse(counter.sideEffectRan, "ignoreOnReplay=true handler must be skipped on loadFromHistory");
    }

    /**
     * 验证<code>counter.sideEffectRan</code>为真，<code>counter.count</code> 等于 <code>0</code>。
     */
    @Test
    void ignoreOnReplayHandlerInvokedOnApply() {
        Counter counter = new Counter();

        counter.triggerSideEffect();

        assertTrue(counter.sideEffectRan, "apply should invoke ignoreOnReplay=true handler");
        assertEquals(0, counter.count, "side-effect event has its own handler and must not touch count");
    }

    /**
     * 验证<code>first.count</code> 等于 <code>1</code>，<code>second.count</code> 等于 <code>1</code>。
     */
    @Test
    void handlerCacheReused() {
        Counter first = new Counter();
        Counter second = new Counter();

        first.trigger();
        second.trigger();

        assertEquals(1, first.count, "handler cache is keyed by aggregate class and shared across instances");
        assertEquals(1, second.count, "instances must keep isolated state while sharing the cached handler map");
    }

    /**
     * 验证<code>applying.count</code> 等于 <code>1</code>，<code>replaying.count</code> 等于 <code>2</code>，<code>replaying.domainEvents().isEmpty()</code>为真。
     */
    @Test
    void privateHandlerAccessible() {
        PrivateHandlerCounter applying = new PrivateHandlerCounter();
        applying.apply(new IncrementEvent());
        assertEquals(1, applying.count, "apply should reach private @EventHandler via setAccessible");

        PrivateHandlerCounter replaying = new PrivateHandlerCounter();
        replaying.loadFromHistory(Arrays.asList(new IncrementEvent(), new IncrementEvent()));
        assertEquals(2, replaying.count, "loadFromHistory should reach private @EventHandler via setAccessible");
        assertTrue(replaying.domainEvents().isEmpty(), "replay must not enqueue events");
    }

    /**
     * 验证<code>counter.apply(new UnhandledEvent())</code> 抛出 <code>IllegalStateException.class</code>，<code>exception.getMessage().contains(UnhandledEvent.class.getName())</code>为真，<code>counter.count</code> 等于 <code>0</code>。
     */
    @Test
    void applyThrowsWhenNoHandlerRegisteredAndDoesNotEnqueue() {
        Counter counter = new Counter();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> counter.apply(new UnhandledEvent()));

        assertTrue(exception.getMessage().contains(UnhandledEvent.class.getName()),
                "message should name the event type");
        assertTrue(exception.getMessage().contains(Counter.class.getName()),
                "message should name the aggregate type");
        assertTrue(counter.domainEvents().isEmpty(), "failed apply must not enqueue the event");
        assertFalse(counter.hasDomainEvents());
        assertEquals(0, counter.count, "no handler means no state mutation");
    }

    /**
     * 测试聚合：普通处理器 + {@code ignoreOnReplay=true} 处理器。
     *
     * <p>两个处理器各绑定一个事件类型（对照 {@link AggregateRootApplyTest} 的
     * OrderCreatedEvent/OrderNotifiedEvent 模式）：处理器映射按事件类型做键，
     * 同一聚合内两个处理器共享同一事件类型时注册结果依赖
     * {@link Class#getDeclaredMethods()} 的未定义顺序，故拆分为独立事件类型。</p>
     */
    static class Counter extends AggregateRoot<String> {

        /**
         * 记录测试操作或处理器执行次数的计数器。
         */
        int count;

        /**
         * 观察被测操作的业务副作用是否发生。
         */
        boolean sideEffectRan;

        /**
         * 为当前用例提供固定的 <code>id</code> 返回值。
         *
         * @return <code>"counter-1"</code>
         */
        @Override
        public String id() {
            return "counter-1";
        }

        /**
         * 调用 <code>apply()</code> 执行当前夹具操作。
         */
        void trigger() {
            apply(new IncrementEvent());
        }

        /**
         * 调用 <code>apply()</code> 执行当前夹具操作。
         */
        void triggerSideEffect() {
            apply(new CountNotifiedEvent());
        }

        /**
         * 处理一次增量事件并把公开处理器的调用计数加一。
         *
         * @param event 由聚合或监听器收到的测试事件
         */
        @EventHandler
        public void on(IncrementEvent event) {
            this.count++;
        }

        /**
         * 根据本次调用更新 <code>this.sideEffectRan = true</code>，用于后续测试步骤。
         *
         * @param event 交给被测入口处理的测试领域事件
         */
        @EventHandler(ignoreOnReplay = true)
        public void onSideEffect(CountNotifiedEvent event) {
            this.sideEffectRan = true;
        }
    }

    /**
     * 处理器为 {@code private} 的聚合：验证 apply 与 loadFromHistory 的
     * {@code setAccessible} 路径（javadoc 宣告 private 处理器可用）。
     */
    static class PrivateHandlerCounter extends AggregateRoot<String> {

        /**
         * 记录测试操作或处理器执行次数的计数器。
         */
        int count;

        /**
         * 为当前用例提供固定的 <code>id</code> 返回值。
         *
         * @return <code>"private-counter-1"</code>
         */
        @Override
        public String id() {
            return "private-counter-1";
        }

        /**
         * 处理一次增量事件并把私有处理器的调用计数加一。
         *
         * @param event 由聚合或监听器收到的测试事件
         */
        @EventHandler
        private void on(IncrementEvent event) {
            this.count++;
        }
    }

    /**
     * 供传输、序列化或处理器分发验证的领域事件样本。
     */
    static class IncrementEvent extends DomainEvent<IncrementEvent.CounterId> {

        /**
         * 使用 <code>super()</code> 初始化 <code>IncrementEvent</code> 测试夹具。
         */
        IncrementEvent() {
            super(new EntityIdPath(new CounterId("counter-1")));
        }

        /**
         * 为测试聚合提供独立标识的值对象。
         */
        final static class CounterId implements AggregateRootId {

            /**
             * Java 序列化使用的版本标识。
             */
            private static final long serialVersionUID = 0L;
            /**
             * 测试聚合注册和反序列化所用的实体类型标识。
             */
            private static final EntityType TYPE = new StringEntityType("Counter");
            /**
             * 组成测试标识的原始值。
             */
            private final String value;

            /**
             * 设置 <code>value</code>，用于构造 <code>CounterId</code> 测试实例。
             *
             * @param value 组成测试标识的原始值
             */
            @JsonCreator()
            CounterId(@JsonProperty("value") String value) {
                this.value = value;
            }

            /**
             * 返回当前值或标识公开的类型。
             *
             * @return 实际返回<code>TYPE</code>，供调用方取得当前值
             */
            @Override
            public EntityType getType() {
                return TYPE;
            }

            /**
             * 返回聚合或实体标识的业务字符串。
             *
             * @return 实际返回<code>value</code>，供调用方取得当前值
             */
            @Override
            public String asString() {
                return value;
            }

            /**
             * 返回带类型前缀的标识字符串。
             *
             * <p>测试夹具会调用 <code>TYPE.asString()</code>。</p>
             *
             * @return 实际返回<code>TYPE.asString() + ":" + value</code>
             */
            @Override
            public String asTypedString() {
                return TYPE.asString() + ":" + value;
            }

            /**
             * 读取<code>value</code> 字段。
             *
             * @return <code>value</code> 字段
             */
            @JsonProperty("value")
            public String value() {
                return value;
            }

            /**
             * 根据<code>value</code>比较对象的值是否相等。
             *
             * <p>只比较运行时类型相同的对象。</p>
             *
             * @param obj 用来比较或转换的输入对象
             * @return 对象满足此实现的相等条件时为真
             */
            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                    return false;
                }
                CounterId other = (CounterId) obj;
                return Objects.equals(this.value, other.value);
            }

            /**
             * 计算与对象相等条件一致的哈希值。
             *
             * @return 由参与相等比较的状态计算出的哈希值
             */
            @Override
            public int hashCode() {
                int result = 0;
                result = 31 * result + Objects.hashCode(value);
                return result;
            }

            /**
             * 生成用于调试和日志的对象描述，包含<code>value</code>。
             *
             * @return 表示当前对象状态的字符串
             */
            @Override
            public String toString() {
                return "CounterId[value=" + value + "]";
            }
        }
    }

    /**
     * 供传输、序列化或处理器分发验证的领域事件样本。
     */
    static class CountNotifiedEvent extends DomainEvent<IncrementEvent.CounterId> {

        /**
         * 使用 <code>super()</code> 初始化 <code>CountNotifiedEvent</code> 测试夹具。
         */
        CountNotifiedEvent() {
            super(new EntityIdPath(new IncrementEvent.CounterId("counter-1")));
        }
    }

    /**
     * 未注册处理器的事件样本，用于验证跳过或分发结果。
     */
    static class UnhandledEvent extends DomainEvent<IncrementEvent.CounterId> {

        /**
         * 使用 <code>super()</code> 初始化 <code>UnhandledEvent</code> 测试夹具。
         */
        UnhandledEvent() {
            super(new EntityIdPath(new IncrementEvent.CounterId("counter-1")));
        }
    }
}
