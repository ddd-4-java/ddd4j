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

    /** \u9a8c\u8bc1<code>counter.count</code> \u7b49\u4e8e <code>1</code>\u3002 */
    @Test
    void eventHandlerInvokedOnce() {
        Counter counter = new Counter();

        counter.trigger();

        assertEquals(1, counter.count, "apply should dispatch the event to @EventHandler exactly once");
    }

    /** \u9a8c\u8bc1<code>counter.count</code> \u7b49\u4e8e <code>3</code>\u3002 */
    @Test
    void eventHandlerInvokedForEachTrigger() {
        Counter counter = new Counter();

        counter.trigger();
        counter.trigger();
        counter.trigger();

        assertEquals(3, counter.count, "each apply should dispatch once");
    }

    /** \u9a8c\u8bc1<code>counter.count</code> \u7b49\u4e8e <code>1</code>\uff0c<code>counter.sideEffectRan</code>\u4e3a\u5047\u3002 */
    @Test
    void ignoreOnReplayHandlerNotInvokedOnLoad() {
        Counter counter = new Counter();

        counter.loadFromHistory(Arrays.asList(new IncrementEvent(), new CountNotifiedEvent()));

        assertEquals(1, counter.count, "normal handler should replay and rebuild state");
        assertFalse(counter.sideEffectRan, "ignoreOnReplay=true handler must be skipped on loadFromHistory");
    }

    /** \u9a8c\u8bc1<code>counter.sideEffectRan</code>\u4e3a\u771f\uff0c<code>counter.count</code> \u7b49\u4e8e <code>0</code>\u3002 */
    @Test
    void ignoreOnReplayHandlerInvokedOnApply() {
        Counter counter = new Counter();

        counter.triggerSideEffect();

        assertTrue(counter.sideEffectRan, "apply should invoke ignoreOnReplay=true handler");
        assertEquals(0, counter.count, "side-effect event has its own handler and must not touch count");
    }

    /** \u9a8c\u8bc1<code>first.count</code> \u7b49\u4e8e <code>1</code>\uff0c<code>second.count</code> \u7b49\u4e8e <code>1</code>\u3002 */
    @Test
    void handlerCacheReused() {
        Counter first = new Counter();
        Counter second = new Counter();

        first.trigger();
        second.trigger();

        assertEquals(1, first.count, "handler cache is keyed by aggregate class and shared across instances");
        assertEquals(1, second.count, "instances must keep isolated state while sharing the cached handler map");
    }

    /** \u9a8c\u8bc1<code>applying.count</code> \u7b49\u4e8e <code>1</code>\uff0c<code>replaying.count</code> \u7b49\u4e8e <code>2</code>\uff0c<code>replaying.domainEvents().isEmpty()</code>\u4e3a\u771f\u3002 */
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

    /** \u9a8c\u8bc1<code>counter.apply(new UnhandledEvent())</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>exception.getMessage().contains(UnhandledEvent.class.getName())</code>\u4e3a\u771f\uff0c<code>counter.count</code> \u7b49\u4e8e <code>0</code>\u3002 */
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

        /** \u8bb0\u5f55\u6d4b\u8bd5\u64cd\u4f5c\u6216\u5904\u7406\u5668\u6267\u884c\u6b21\u6570\u7684\u8ba1\u6570\u5668\u3002 */
        int count;

        /** \u89c2\u5bdf\u88ab\u6d4b\u64cd\u4f5c\u7684\u4e1a\u52a1\u526f\u4f5c\u7528\u662f\u5426\u53d1\u751f\u3002 */
        boolean sideEffectRan;

        /**
         * \u4e3a\u5f53\u524d\u7528\u4f8b\u63d0\u4f9b\u56fa\u5b9a\u7684 <code>id</code> \u8fd4\u56de\u503c\u3002
         * @return <code>"counter-1"</code>
         */
        @Override
        public String id() {
            return "counter-1";
        }

        /** \u8c03\u7528 <code>apply()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002 */
        void trigger() {
            apply(new IncrementEvent());
        }

        /** \u8c03\u7528 <code>apply()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002 */
        void triggerSideEffect() {
            apply(new CountNotifiedEvent());
        }

        /**
         * \u5904\u7406\u4e00\u6b21\u589e\u91cf\u4e8b\u4ef6\u5e76\u628a\u516c\u5f00\u5904\u7406\u5668\u7684\u8c03\u7528\u8ba1\u6570\u52a0\u4e00\u3002
         * @param event \u7531\u805a\u5408\u6216\u76d1\u542c\u5668\u6536\u5230\u7684\u6d4b\u8bd5\u4e8b\u4ef6
         */
        @EventHandler
        public void on(IncrementEvent event) {
            this.count++;
        }

        /**
         * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>this.sideEffectRan = true</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
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

        /** \u8bb0\u5f55\u6d4b\u8bd5\u64cd\u4f5c\u6216\u5904\u7406\u5668\u6267\u884c\u6b21\u6570\u7684\u8ba1\u6570\u5668\u3002 */
        int count;

        /**
         * \u4e3a\u5f53\u524d\u7528\u4f8b\u63d0\u4f9b\u56fa\u5b9a\u7684 <code>id</code> \u8fd4\u56de\u503c\u3002
         * @return <code>"private-counter-1"</code>
         */
        @Override
        public String id() {
            return "private-counter-1";
        }

        /**
         * \u5904\u7406\u4e00\u6b21\u589e\u91cf\u4e8b\u4ef6\u5e76\u628a\u79c1\u6709\u5904\u7406\u5668\u7684\u8c03\u7528\u8ba1\u6570\u52a0\u4e00\u3002
         * @param event \u7531\u805a\u5408\u6216\u76d1\u542c\u5668\u6536\u5230\u7684\u6d4b\u8bd5\u4e8b\u4ef6
         */
        @EventHandler
        private void on(IncrementEvent event) {
            this.count++;
        }
    }

    /** \u4f9b\u4f20\u8f93\u3001\u5e8f\u5217\u5316\u6216\u5904\u7406\u5668\u5206\u53d1\u9a8c\u8bc1\u7684\u9886\u57df\u4e8b\u4ef6\u6837\u672c\u3002 */
    static class IncrementEvent extends DomainEvent<IncrementEvent.CounterId> {

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>IncrementEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        IncrementEvent() {
            super(new EntityIdPath(new CounterId("counter-1")));
        }

        /** \u4e3a\u6d4b\u8bd5\u805a\u5408\u63d0\u4f9b\u72ec\u7acb\u6807\u8bc6\u7684\u503c\u5bf9\u8c61\u3002 */
        final static class CounterId implements AggregateRootId {

            /** Java \u5e8f\u5217\u5316\u4f7f\u7528\u7684\u7248\u672c\u6807\u8bc6\u3002 */
            private static final long serialVersionUID = 0L;
            /** \u6d4b\u8bd5\u805a\u5408\u6ce8\u518c\u548c\u53cd\u5e8f\u5217\u5316\u6240\u7528\u7684\u5b9e\u4f53\u7c7b\u578b\u6807\u8bc6\u3002 */
            private static final EntityType TYPE = new StringEntityType("Counter");
            /** \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c\u3002 */
            private final String value;

            /**
             * \u8bbe\u7f6e <code>value</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>CounterId</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
             * @param value \u7ec4\u6210\u6d4b\u8bd5\u6807\u8bc6\u7684\u539f\u59cb\u503c
             */
            @JsonCreator()
            CounterId(@JsonProperty("value") String value) {
                this.value = value;
            }

            /** {@inheritDoc} */
            @Override
            public EntityType getType() {
                return TYPE;
            }

            /** {@inheritDoc} */
            @Override
            public String asString() {
                return value;
            }

            /** {@inheritDoc} */
            @Override
            public String asTypedString() {
                return TYPE.asString() + ":" + value;
            }

            /**
             * \u8bfb\u53d6<code>value</code> \u5b57\u6bb5\u3002
             * @return <code>value</code> \u5b57\u6bb5
             */
            @JsonProperty("value")
            public String value() {
                return value;
            }

            /** {@inheritDoc} */
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

            /** {@inheritDoc} */
            @Override
            public int hashCode() {
                int result = 0;
                result = 31 * result + Objects.hashCode(value);
                return result;
            }

            /** {@inheritDoc} */
            @Override
            public String toString() {
                return "CounterId[value=" + value + "]";
            }
        }
    }

    /** \u4f9b\u4f20\u8f93\u3001\u5e8f\u5217\u5316\u6216\u5904\u7406\u5668\u5206\u53d1\u9a8c\u8bc1\u7684\u9886\u57df\u4e8b\u4ef6\u6837\u672c\u3002 */
    static class CountNotifiedEvent extends DomainEvent<IncrementEvent.CounterId> {

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>CountNotifiedEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        CountNotifiedEvent() {
            super(new EntityIdPath(new IncrementEvent.CounterId("counter-1")));
        }
    }

    /** \u672a\u6ce8\u518c\u5904\u7406\u5668\u7684\u4e8b\u4ef6\u6837\u672c\uff0c\u7528\u4e8e\u9a8c\u8bc1\u8df3\u8fc7\u6216\u5206\u53d1\u7ed3\u679c\u3002 */
    static class UnhandledEvent extends DomainEvent<IncrementEvent.CounterId> {

        /** \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>UnhandledEvent</code> \u6d4b\u8bd5\u5939\u5177\u3002 */
        UnhandledEvent() {
            super(new EntityIdPath(new IncrementEvent.CounterId("counter-1")));
        }
    }
}
