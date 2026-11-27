package io.ddd4j.core.cqrs.readmodel;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通过 <code>Collections.emptyList()</code>、<code>dispatcher.size()</code>、<code>dispatcher.dispatch()</code> 验证 <code>TypedEventDispatcherParity</code> 的运行行为。
 */
class TypedEventDispatcherParityTest {

    /**
     * 验证<code>dispatcher.size()</code> 等于 <code>0</code>，<code>dispatcher.dispatch("created", new CreatedEvent())</code>为假，<code>dispatcher.dispatch((TypedEvent) null)</code> 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void emptyHandlersAndUnknownEventsAreIgnored() {
        TypedEventDispatcher dispatcher = new TypedEventDispatcher(Collections.emptyList());

        assertEquals(0, dispatcher.size());
        assertFalse(dispatcher.dispatch("created", new CreatedEvent()));
        assertFalse(dispatcher.dispatch(" ", new CreatedEvent()));
        assertFalse(dispatcher.dispatch("created", null));
        assertEquals(0, dispatcher.dispatchAll(Collections.emptyList()));
        assertThrows(NullPointerException.class, () -> dispatcher.dispatch((TypedEvent) null));
    }

    /**
     * 验证<code>dispatcher.size()</code> 等于 <code>1</code>，<code>dispatcher.dispatch(new CreatedEvent())</code>为真，<code>last.get()</code> 等于 <code>1</code>。
     */
    @Test
    void duplicateEventTypeUsesTheLastHandler() {
        AtomicInteger first = new AtomicInteger();
        AtomicInteger last = new AtomicInteger();
        TypedEventDispatcher dispatcher = new TypedEventDispatcher(Arrays.asList(
                new CreatedHandler(first), new CreatedHandler(last)));

        assertEquals(1, dispatcher.size());
        assertTrue(dispatcher.dispatch(new CreatedEvent()));
        assertEquals(0, first.get());
        assertEquals(1, last.get());
    }

    /**
     * 验证<code>dispatcher.dispatchAll(Arrays.asList( new CreatedEvent(), new UnknownEvent(), new CreatedEvent()))</code> 等于 <code>2</code>，<code>handled.get()</code> 等于 <code>2</code>。
     */
    @Test
    void dispatchAllCountsOnlyHandledEvents() {
        AtomicInteger handled = new AtomicInteger();
        TypedEventDispatcher dispatcher = new TypedEventDispatcher(
                Collections.singletonList(new CreatedHandler(handled)));

        assertEquals(2, dispatcher.dispatchAll(Arrays.asList(
                new CreatedEvent(), new UnknownEvent(), new CreatedEvent())));
        assertEquals(2, handled.get());
    }

    /**
     * 验证被测调用 抛出 <code>NullPointerException.class</code>，被测调用 抛出 <code>IllegalArgumentException.class</code>，<code>dispatcher.dispatch("created", "wrong class")</code> 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void invalidHandlersAndMismatchedEventClassAreRejected() {
        assertThrows(NullPointerException.class,
                () -> new TypedEventDispatcher(Collections.singletonList(null)));
        assertThrows(IllegalArgumentException.class,
                () -> new TypedEventDispatcher(Collections.singletonList(
                        new CreatedHandler(new AtomicInteger(), " "))));

        TypedEventDispatcher dispatcher = new TypedEventDispatcher(
                Collections.singletonList(new CreatedHandler(new AtomicInteger())));
        assertThrows(IllegalArgumentException.class,
                () -> dispatcher.dispatch("created", "wrong class"));
    }

    /**
     * 实现 <code>TypedEvent</code>，供测试调用 <code>getEventType()</code> 并观察结果。
     */
    private static final class CreatedEvent implements TypedEvent {
        /**
         * 返回用于识别此领域事件的事件类型。
         *
         * @return 实际返回<code>"created"</code>，供调用方取得当前值
         */
        @Override
        public String getEventType() {
            return "created";
        }
    }

    /**
     * 实现 <code>TypedEvent</code>，供测试调用 <code>getEventType()</code> 并观察结果。
     */
    private static final class UnknownEvent implements TypedEvent {
        /**
         * 返回用于识别此领域事件的事件类型。
         *
         * @return 实际返回<code>"unknown"</code>，供调用方取得当前值
         */
        @Override
        public String getEventType() {
            return "unknown";
        }
    }

    /**
     * 实现 <code>TypedEventHandler</code>，供测试调用 <code>getEventType()</code>、<code>getEventClass()</code>、<code>handle()</code> 并观察结果。
     */
    private static final class CreatedHandler implements TypedEventHandler<CreatedEvent> {
        /**
         * 当前测试中已经交给处理器的记录。
         */
        private final AtomicInteger handled;
        /**
         * 区分当前测试事件的逻辑类型。
         */
        private final String eventType;

        /**
         * 使用 <code>this()</code> 初始化 <code>CreatedHandler</code> 测试夹具。
         *
         * @param handled 当前测试中已经交给处理器的记录
         */
        private CreatedHandler(AtomicInteger handled) {
            this(handled, "created");
        }

        /**
         * 用给定值初始化 <code>CreatedHandler</code> 实例。
         *
         * @param handled   当前测试中已经交给处理器的记录
         * @param eventType <code>eventType</code> 字段
         */
        private CreatedHandler(AtomicInteger handled, String eventType) {
            this.handled = handled;
            this.eventType = eventType;
        }

        /**
         * 返回用于识别此领域事件的事件类型。
         *
         * @return 实际返回<code>eventType</code>，供调用方取得当前值
         */
        @Override
        public String getEventType() {
            return eventType;
        }

        /**
         * 返回当前监听器可以消费的事件类型。
         *
         * @return 实际返回<code>CreatedEvent.class</code>，供调用方取得当前值
         */
        @Override
        public Class<CreatedEvent> getEventClass() {
            return CreatedEvent.class;
        }

        /**
         * 处理调用方传入的消息或请求。
         *
         * <p>测试夹具会调用 <code>handled.incrementAndGet()</code>。</p>
         *
         * @param event 事件
         */
        @Override
        public void handle(CreatedEvent event) {
            handled.incrementAndGet();
        }
    }
}
