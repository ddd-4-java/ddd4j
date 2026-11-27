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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link EventChunk} 单元测试。
 *
 * <p>覆盖构造器校验、工厂方法、不可变性以及事件查询。
 *
 * @author PartMe.AI
 */
@DisplayName("EventChunk")
class EventChunkTest {

    /**
     * 构造器用例：参数校验、负数拒绝、空列表语义与防御性拷贝。
     */
    @Nested
    @DisplayName("构造器")
    class Constructor {

        /** \u9a8c\u8bc1<code>chunk.getEvents()</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"e1"</code>\u3001<code>"e2"</code>\u3001<code>"e3"</code>\uff0c<code>chunk.getNextEventNumber()</code> \u7b49\u4e8e <code>5L</code>\u3002 */
        @Test
        void 构造器_正常事件与位置_应保存事件并返回位置() {
            List<String> events = Arrays.asList("e1", "e2", "e3");

            EventChunk<String> chunk = new EventChunk<>(events, 5);

            assertThat(chunk.getEvents()).containsExactly("e1", "e2", "e3");
            assertThat(chunk.getNextEventNumber()).isEqualTo(5L);
        }

        /** \u9a8c\u8bc1<code>chunk.getNextEventNumber()</code>\u4e3a\u96f6\u3002 */
        @Test
        void 构造器_nextEventNumber为零_应允许() {
            EventChunk<String> chunk = new EventChunk<>(Collections.singletonList("e1"), 0);

            assertThat(chunk.getNextEventNumber()).isZero();
        }

        /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
        @Test
        void 构造器_nextEventNumber为负数_应抛IllegalArgumentException() {
            assertThatThrownBy(() -> new EventChunk<>(Collections.singletonList("e1"), -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nextEventNumber must not be negative");
        }

        /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
        @Test
        void 构造器_events为null_应抛NullPointerException() {
            assertThatThrownBy(() -> new EventChunk<String>(null, 1))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("events must not be null");
        }

        /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
        @Test
        void 构造器_事件列表含null_应拒绝() {
            assertThatThrownBy(() -> new EventChunk<>(Arrays.asList("e1", null), 2))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("events must not contain null");
        }

        /** \u9a8c\u8bc1<code>chunk.getEvents()</code>\u4e3a\u7a7a\uff0c<code>chunk.hasEvents()</code>\u4e3a\u5047\u3002 */
        @Test
        void 构造器_events为空列表_应创建空chunk且hasEvents为false() {
            EventChunk<String> chunk = new EventChunk<>(Arrays.asList(), 0);

            assertThat(chunk.getEvents()).isEmpty();
            assertThat(chunk.hasEvents()).isFalse();
        }

        /** \u9a8c\u8bc1<code>chunk.getEvents()</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"e1"</code>\u3002 */
        @Test
        void 构造器_传入可变List_应做防御性拷贝_原列表修改不影响chunk() {
            List<String> mutable = new ArrayList<>();
            mutable.add("e1");
            EventChunk<String> chunk = new EventChunk<>(mutable, 1);

            mutable.add("e2");

            assertThat(chunk.getEvents()).containsExactly("e1");
        }
    }

    /**
     * empty 工厂方法用例：返回不含事件的 chunk 并保留传入的下一个事件号。
     */
    @Nested
    @DisplayName("empty 工厂方法")
    class EmptyFactory {

        /** \u9a8c\u8bc1<code>chunk.getEvents()</code>\u4e3a\u7a7a\uff0c<code>chunk.hasEvents()</code>\u4e3a\u5047\u3002 */
        @Test
        void empty_应返回不含事件的chunk() {
            EventChunk<String> chunk = EventChunk.empty(10);

            assertThat(chunk.getEvents()).isEmpty();
            assertThat(chunk.hasEvents()).isFalse();
        }

        /** \u9a8c\u8bc1<code>chunk.getNextEventNumber()</code> \u7b49\u4e8e <code>42L</code>\u3002 */
        @Test
        void empty_应保留传入的nextEventNumber() {
            EventChunk<String> chunk = EventChunk.empty(42);

            assertThat(chunk.getNextEventNumber()).isEqualTo(42L);
        }

        /** \u9a8c\u8bc1<code>chunk.getNextEventNumber()</code>\u4e3a\u96f6\u3002 */
        @Test
        void empty_nextEventNumber为零_应允许() {
            EventChunk<String> chunk = EventChunk.empty(0);

            assertThat(chunk.getNextEventNumber()).isZero();
        }

        /** \u9a8c\u8bc1<code>EventChunk.empty(-1)</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
        @Test
        void empty_nextEventNumber为负数_应抛IllegalArgumentException() {
            assertThatThrownBy(() -> EventChunk.empty(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * 事件查询 API 用例：hasEvents 判空、getEvents 不可变视图与下一个事件号回读。
     */
    @Nested
    @DisplayName("hasEvents / getEvents")
    class QueryApi {

        /** \u9a8c\u8bc1<code>chunk.hasEvents()</code>\u4e3a\u771f\u3002 */
        @Test
        void hasEvents_事件非空_应返回true() {
            EventChunk<String> chunk = new EventChunk<>(Collections.singletonList("e1"), 1);

            assertThat(chunk.hasEvents()).isTrue();
        }

        /** \u9a8c\u8bc1<code>chunk.getEvents().add("e3")</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\u3002 */
        @Test
        void getEvents_返回不可变List_修改应抛UnsupportedOperationException() {
            EventChunk<String> chunk = new EventChunk<>(Arrays.asList("e1", "e2"), 2);

            assertThatThrownBy(() -> chunk.getEvents().add("e3"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        /** \u9a8c\u8bc1<code>chunk.getEvents().add("x")</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\u3002 */
        @Test
        void getEvents_empty返回的List_修改应抛UnsupportedOperationException() {
            EventChunk<String> chunk = EventChunk.empty(0);

            assertThatThrownBy(() -> chunk.getEvents().add("x"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        /** \u9a8c\u8bc1<code>chunk.getNextEventNumber()</code> \u7b49\u4e8e <code>99L</code>\u3002 */
        @Test
        void getNextEventNumber_应返回构造时传入的值() {
            EventChunk<String> chunk = new EventChunk<>(Collections.singletonList("e1"), 99L);

            assertThat(chunk.getNextEventNumber()).isEqualTo(99L);
        }
    }
}
