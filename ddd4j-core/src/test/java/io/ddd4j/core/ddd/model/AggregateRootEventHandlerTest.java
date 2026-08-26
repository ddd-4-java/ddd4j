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

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityId;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.EventHandler;
import io.ddd4j.core.ddd.event.StringEntityType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AggregateRoot} 事件处理器机制的全覆盖测试：{@code @EventHandler}
 * 派发频次、{@code ignoreOnReplay} 语义、处理器缓存复用、private 处理器可达性
 * 与派发失败不入队。
 *
 * <p>与 {@link AggregateRootApplyTest} 互补：那边验证 apply/loadFromHistory 的
 * 基本契约，这里按 Counter/IncrementEvent 场景逐一覆盖处理器机制本身。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
class AggregateRootEventHandlerTest {

    @Test
    void eventHandlerInvokedOnce() {
        Counter counter = new Counter();

        counter.trigger();

        assertEquals(1, counter.count, "apply should dispatch the event to @EventHandler exactly once");
    }

    @Test
    void eventHandlerInvokedForEachTrigger() {
        Counter counter = new Counter();

        counter.trigger();
        counter.trigger();
        counter.trigger();

        assertEquals(3, counter.count, "each apply should dispatch once");
    }

    @Test
    void ignoreOnReplayHandlerNotInvokedOnLoad() {
        Counter counter = new Counter();

        counter.loadFromHistory(List.of(new IncrementEvent(), new CountNotifiedEvent()));

        assertEquals(1, counter.count, "normal handler should replay and rebuild state");
        assertFalse(counter.sideEffectRan, "ignoreOnReplay=true handler must be skipped on loadFromHistory");
    }

    @Test
    void ignoreOnReplayHandlerInvokedOnApply() {
        Counter counter = new Counter();

        counter.triggerSideEffect();

        assertTrue(counter.sideEffectRan, "apply should invoke ignoreOnReplay=true handler");
        assertEquals(0, counter.count, "side-effect event has its own handler and must not touch count");
    }

    @Test
    void handlerCacheReused() {
        Counter first = new Counter();
        Counter second = new Counter();

        first.trigger();
        second.trigger();

        assertEquals(1, first.count, "handler cache is keyed by aggregate class and shared across instances");
        assertEquals(1, second.count, "instances must keep isolated state while sharing the cached handler map");
    }

    @Test
    void privateHandlerAccessible() {
        PrivateHandlerCounter applying = new PrivateHandlerCounter();
        applying.apply(new IncrementEvent());
        assertEquals(1, applying.count, "apply should reach private @EventHandler via setAccessible");

        PrivateHandlerCounter replaying = new PrivateHandlerCounter();
        replaying.loadFromHistory(List.of(new IncrementEvent(), new IncrementEvent()));
        assertEquals(2, replaying.count, "loadFromHistory should reach private @EventHandler via setAccessible");
        assertTrue(replaying.domainEvents().isEmpty(), "replay must not enqueue events");
    }

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

        int count;

        boolean sideEffectRan;

        @Override
        public String id() {
            return "counter-1";
        }

        void trigger() {
            apply(new IncrementEvent());
        }

        void triggerSideEffect() {
            apply(new CountNotifiedEvent());
        }

        @EventHandler
        public void on(IncrementEvent event) {
            this.count++;
        }

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

        int count;

        @Override
        public String id() {
            return "private-counter-1";
        }

        @EventHandler
        private void on(IncrementEvent event) {
            this.count++;
        }
    }

    static class IncrementEvent extends DomainEvent<IncrementEvent.CounterId> {

        IncrementEvent() {
            super(new EntityIdPath(new CounterId("counter-1")));
        }

        record CounterId(String value) implements EntityId {

            private static final EntityType TYPE = new StringEntityType("Counter");

            @Override
            public EntityType getType() {
                return TYPE;
            }

            @Override
            public String asString() {
                return value;
            }

            @Override
            public String asTypedString() {
                return TYPE.asString() + ":" + value;
            }
        }
    }

    static class CountNotifiedEvent extends DomainEvent<IncrementEvent.CounterId> {

        CountNotifiedEvent() {
            super(new EntityIdPath(new IncrementEvent.CounterId("counter-1")));
        }
    }

    static class UnhandledEvent extends DomainEvent<IncrementEvent.CounterId> {

        UnhandledEvent() {
            super(new EntityIdPath(new IncrementEvent.CounterId("counter-1")));
        }
    }
}
