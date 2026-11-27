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

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** \u901a\u8fc7 <code>Arrays.asList()</code>\u3001<code>dispatcher.dispatch()</code>\u3001<code>handled.get()</code> \u9a8c\u8bc1 <code>TypedEventDispatcher</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class TypedEventDispatcherTest {

    /** \u9a8c\u8bc1<code>dispatched</code>\u4e3a\u771f\uff0c<code>handled.get()</code> \u7b49\u4e8e <code>1</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>dispatched</code>\u4e3a\u5047\u3002 */
    @Test
    void shouldIgnoreUnknownEventType() {
        TypedEventDispatcher dispatcher = new TypedEventDispatcher(Arrays.asList());

        boolean dispatched = dispatcher.dispatch(new PersonCreatedEvent("p-1"));

        assertFalse(dispatched);
    }

    /** \u9a8c\u8bc1<code>dispatcher.dispatch("person.created", "wrong")</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void shouldRejectEventClassMismatch() {
        TypedEventDispatcher dispatcher = new TypedEventDispatcher(Arrays.asList(
                new PersonCreatedHandler(new AtomicInteger())
        ));

        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch("person.created", "wrong"));
    }

    /** \u5b9e\u73b0 <code>TypedEvent</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>getEventType()</code>\u3001<code>id()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    final static class PersonCreatedEvent implements TypedEvent {

        /** Java \u5e8f\u5217\u5316\u4f7f\u7528\u7684\u7248\u672c\u6807\u8bc6\u3002 */
        private static final long serialVersionUID = 0L;

        /** \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6\u3002 */
        private final String id;

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
         * \u8bbe\u7f6e <code>id</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>PersonCreatedEvent</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param id \u6807\u8bc6\u5f53\u524d\u6d4b\u8bd5\u5bf9\u8c61\u7684\u4e3b\u952e\u6216\u4e8b\u4ef6\u6807\u8bc6
         */
        @JsonCreator()
        PersonCreatedEvent(@JsonProperty("id") String id) {
            this.id = id;
        }

        /**
         * \u8bfb\u53d6<code>id</code> \u5b57\u6bb5\u3002
         * @return <code>id</code> \u5b57\u6bb5
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

    /** \u5b9e\u73b0 <code>TypedEventHandler</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>getEventType()</code>\u3001<code>getEventClass()</code>\u3001<code>handle()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    static class PersonCreatedHandler implements TypedEventHandler<PersonCreatedEvent> {

        /** \u5f53\u524d\u6d4b\u8bd5\u4e2d\u5df2\u7ecf\u4ea4\u7ed9\u5904\u7406\u5668\u7684\u8bb0\u5f55\u3002 */
        private final AtomicInteger handled;

        /**
         * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>PersonCreatedHandler</code> \u5b9e\u4f8b\u3002
         * @param handled \u5f53\u524d\u6d4b\u8bd5\u4e2d\u5df2\u7ecf\u4ea4\u7ed9\u5904\u7406\u5668\u7684\u8bb0\u5f55
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
