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
package io.ddd4j.core.cqrs.readmodel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通过 <code>Arrays.asList()</code>、<code>dispatcher.dispatch()</code>、<code>handled.get()</code> 验证 <code>TypedEventDispatcher</code> 的运行行为。
 */
class TypedEventDispatcherTest {

    /**
     * 验证<code>dispatched</code>为真，<code>handled.get()</code> 等于 <code>1</code>。
     */
    @Test
    void shouldDispatchMatchingEvent() {
        AtomicInteger handled = new AtomicInteger();
        TypedEventDispatcher dispatcher = new TypedEventDispatcher(Arrays.asList(
                new PersonCreatedHandler(handled)
        ));

        boolean dispatched = dispatcher.dispatch(new PersonCreatedEvent("p-1"));

        assertTrue(dispatched);
        assertEquals(1, handled.get());
    }

    /**
     * 验证<code>dispatched</code>为假。
     */
    @Test
    void shouldIgnoreUnknownEventType() {
        TypedEventDispatcher dispatcher = new TypedEventDispatcher(Arrays.asList());

        boolean dispatched = dispatcher.dispatch(new PersonCreatedEvent("p-1"));

        assertFalse(dispatched);
    }

    /**
     * 验证<code>dispatcher.dispatch("person.created", "wrong")</code> 抛出 <code>IllegalArgumentException.class</code>。
     */
    @Test
    void shouldRejectEventClassMismatch() {
        TypedEventDispatcher dispatcher = new TypedEventDispatcher(Arrays.asList(
                new PersonCreatedHandler(new AtomicInteger())
        ));

        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch("person.created", "wrong"));
    }

    /**
     * 实现 <code>TypedEvent</code>，供测试调用 <code>getEventType()</code>、<code>id()</code> 并观察结果。
     */
    final static class PersonCreatedEvent implements TypedEvent {

        /**
         * Java 序列化使用的版本标识。
         */
        private static final long serialVersionUID = 0L;

        /**
         * 标识当前测试对象的主键或事件标识。
         */
        private final String id;

        /**
         * 设置 <code>id</code>，用于构造 <code>PersonCreatedEvent</code> 测试实例。
         *
         * @param id 标识当前测试对象的主键或事件标识
         */
        @JsonCreator()
        PersonCreatedEvent(@JsonProperty("id") String id) {
            this.id = id;
        }

        /**
         * 返回用于识别此领域事件的事件类型。
         *
         * @return 实际返回<code>"person.created"</code>，供调用方取得当前值
         */
        @Override
        public String getEventType() {
            return "person.created";
        }

        /**
         * 读取<code>id</code> 字段。
         *
         * @return <code>id</code> 字段
         */
        @JsonProperty("id")
        public String id() {
            return id;
        }

        /**
         * 根据<code>id</code>比较对象的值是否相等。
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
            PersonCreatedEvent other = (PersonCreatedEvent) obj;
            return Objects.equals(this.id, other.id);
        }

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(id);
            return result;
        }

        /**
         * 生成用于调试和日志的对象描述，包含<code>id</code>。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return "PersonCreatedEvent[id=" + id + "]";
        }
    }

    /**
     * 实现 <code>TypedEventHandler</code>，供测试调用 <code>getEventType()</code>、<code>getEventClass()</code>、<code>handle()</code> 并观察结果。
     */
    static class PersonCreatedHandler implements TypedEventHandler<PersonCreatedEvent> {

        /**
         * 当前测试中已经交给处理器的记录。
         */
        private final AtomicInteger handled;

        /**
         * 用给定值初始化 <code>PersonCreatedHandler</code> 实例。
         *
         * @param handled 当前测试中已经交给处理器的记录
         */
        PersonCreatedHandler(AtomicInteger handled) {
            this.handled = handled;
        }

        /**
         * 返回用于识别此领域事件的事件类型。
         *
         * @return 实际返回<code>"person.created"</code>，供调用方取得当前值
         */
        @Override
        public String getEventType() {
            return "person.created";
        }

        /**
         * 返回当前监听器可以消费的事件类型。
         *
         * @return 实际返回<code>PersonCreatedEvent.class</code>，供调用方取得当前值
         */
        @Override
        public Class<PersonCreatedEvent> getEventClass() {
            return PersonCreatedEvent.class;
        }

        /**
         * 处理调用方传入的消息或请求。
         *
         * <p>测试夹具会调用 <code>handled.incrementAndGet()</code>。</p>
         *
         * @param event 事件
         */
        @Override
        public void handle(PersonCreatedEvent event) {
            handled.incrementAndGet();
        }
    }
}
