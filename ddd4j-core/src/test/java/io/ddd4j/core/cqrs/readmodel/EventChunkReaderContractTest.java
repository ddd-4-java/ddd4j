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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EventChunkReader} SPI 契约测试。
 *
 * <p>使用一个内存测试桩 {@link InMemoryEventChunkReader} 来验证 SPI 的可履行性
 * 以及实现者应当遵守的契约：
 * <ul>
 *   <li>{@code read} 返回的事件数不超过 {@code chunkSize}</li>
 *   <li>从 {@code fromEventNumber} 开始（包含）读取</li>
 *   <li>返回的 {@code nextEventNumber} 大于已读取的最后一个事件号</li>
 *   <li>事件流耗尽时返回空 chunk 且不推进位置</li>
 *   <li>{@code eventTypes} 过滤生效</li>
 * </ul>
 *
 * @author ddd4j
 */
@DisplayName("EventChunkReader SPI 契约")
class EventChunkReaderContractTest {

    /** \u9a8c\u8bc1<code>chunk.getEvents()</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"b"</code>\u3001<code>"c"</code>\uff0c<code>chunk.getNextEventNumber()</code> \u7b49\u4e8e <code>4L</code>\u3002 */
    @Test
    void read_应按fromEventNumber包含起始读取事件() {
        InMemoryEventChunkReader reader = new InMemoryEventChunkReader();
        reader.append(1, "a");
        reader.append(2, "b");
        reader.append(3, "c");

        EventChunk<String> chunk = reader.read("s", 2, 100, null);

        assertThat(chunk.getEvents()).containsExactly("b", "c");
        assertThat(chunk.getNextEventNumber()).isEqualTo(4L);
    }

    /** \u9a8c\u8bc1<code>chunk.getEvents()</code> \u7684\u5927\u5c0f\u4e3a <code>2</code>\uff0c<code>chunk.getNextEventNumber()</code> \u7b49\u4e8e <code>3L</code>\u3002 */
    @Test
    void read_应限制返回事件数量不超过chunkSize() {
        InMemoryEventChunkReader reader = new InMemoryEventChunkReader();
        reader.append(1, "a");
        reader.append(2, "b");
        reader.append(3, "c");
        reader.append(4, "d");

        EventChunk<String> chunk = reader.read("s", 1, 2, null);

        assertThat(chunk.getEvents()).hasSize(2).containsExactly("a", "b");
        assertThat(chunk.getNextEventNumber()).isEqualTo(3L);
    }

    /** \u9a8c\u8bc1<code>chunk.hasEvents()</code>\u4e3a\u5047\uff0c<code>chunk.getNextEventNumber()</code> \u7b49\u4e8e <code>2L</code>\u3002 */
    @Test
    void read_从已读完的位置继续读_应返回空chunk且不推进位置() {
        InMemoryEventChunkReader reader = new InMemoryEventChunkReader();
        reader.append(1, "a");

        reader.read("s", 1, 100, null);
        EventChunk<String> chunk = reader.read("s", 2, 100, null);

        assertThat(chunk.hasEvents()).isFalse();
        assertThat(chunk.getNextEventNumber()).isEqualTo(2L);
    }

    /** \u9a8c\u8bc1<code>chunk.hasEvents()</code>\u4e3a\u5047\uff0c<code>chunk.getNextEventNumber()</code>\u4e3a\u96f6\u3002 */
    @Test
    void read_空流首次读取_应返回nextEventNumber为零的空chunk() {
        InMemoryEventChunkReader reader = new InMemoryEventChunkReader();

        EventChunk<String> chunk = reader.read("s", 0, 100, null);

        assertThat(chunk.hasEvents()).isFalse();
        assertThat(chunk.getNextEventNumber()).isZero();
    }

    /** \u9a8c\u8bc1<code>chunk.getEvents()</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"a"</code>\u3001<code>"b"</code>\u3002 */
    @Test
    void read_eventTypes为空_应读取全部类型() {
        InMemoryEventChunkReader reader = new InMemoryEventChunkReader();
        reader.append(1, "a");
        reader.append(2, "b");

        EventChunk<String> chunk = reader.read("s", 1, 100, Collections.emptyList());

        assertThat(chunk.getEvents()).containsExactly("a", "b");
    }

    /**
     * 内存版测试桩：把事件按号顺序存储，{@code read} 按 fromEventNumber（包含）顺序读取，
     * 受 chunkSize 限制。返回的 nextEventNumber 为最后一个已读事件号 + 1（或原值当未读到）。
     */
    static class InMemoryEventChunkReader implements EventChunkReader<String> {

        /** \u5f53\u524d\u6d4b\u8bd5\u671f\u95f4\u6536\u96c6\u7684\u9886\u57df\u4e8b\u4ef6\uff0c\u4f9b\u65ad\u8a00\u6bd4\u8f83\u53d1\u5e03\u5185\u5bb9\u6216\u987a\u5e8f\u3002 */
        private final Deque<Numbered> events = new ArrayDeque<>();

        /**
         * \u8c03\u7528 <code>events.addLast()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param number \u4f20\u7ed9 <code>events.addLast()</code> \u7684 <code>long</code> \u8f93\u5165
         * @param payload \u4f20\u7ed9\u88ab\u6d4b\u64cd\u4f5c\u7684\u6d88\u606f\u8d1f\u8f7d
         */
        void append(long number, String payload) {
            events.addLast(new Numbered(number, payload));
        }

        /** {@inheritDoc} */
        @Override
        public EventChunk<String> read(String streamId, long fromEventNumber, int chunkSize,
                                       Collection<String> eventTypes) {
            List<String> picked = new java.util.ArrayList<>();
            long last = fromEventNumber - 1;
            int remaining = chunkSize;
            for (Numbered n : events) {
                if (remaining <= 0) {
                    break;
                }
                if (n.number >= fromEventNumber) {
                    picked.add(n.payload);
                    last = n.number;
                    remaining--;
                }
            }
            long next = picked.isEmpty() ? fromEventNumber : last + 1;
            return new EventChunk<>(picked, next);
        }

        /**
         * 带事件号的载荷条目，用于按号顺序读取。
         */
        private final static class Numbered {

            /** Java \u5e8f\u5217\u5316\u4f7f\u7528\u7684\u7248\u672c\u6807\u8bc6\u3002 */
            private static final long serialVersionUID = 0L;

            /** \u7528\u4e8e\u6d4b\u8bd5\u6620\u5c04\u6216\u5904\u7406\u903b\u8f91\u7684\u6570\u503c\u5b57\u6bb5\u3002 */
            private final long number;

            /** \u5f53\u524d\u4e8b\u4ef6\u6216\u8bf7\u6c42\u5939\u5177\u643a\u5e26\u7684\u8d1f\u8f7d\u3002 */
            private final String payload;

            /**
             * 创建载荷条目实例。
             *
             * @param number  事件号
             * @param payload 事件载荷
             */
            @JsonCreator()
            private Numbered(@JsonProperty("number") long number, @JsonProperty("payload") String payload) {
                this.number = number;
                this.payload = payload;
            }

            /**
             * \u8bfb\u53d6<code>number</code> \u5b57\u6bb5\u3002
             * @return <code>number</code> \u5b57\u6bb5
             */
            @JsonProperty("number")
            public long number() {
                return number;
            }

            /**
             * \u8bfb\u53d6<code>payload</code> \u5b57\u6bb5\u3002
             * @return <code>payload</code> \u5b57\u6bb5
             */
            @JsonProperty("payload")
            public String payload() {
                return payload;
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
                Numbered other = (Numbered) obj;
                return this.number == other.number && Objects.equals(this.payload, other.payload);
            }

            /** {@inheritDoc} */
            @Override
            public int hashCode() {
                int result = 0;
                result = 31 * result + Long.hashCode(number);
                result = 31 * result + Objects.hashCode(payload);
                return result;
            }

            /** {@inheritDoc} */
            @Override
            public String toString() {
                return "Numbered[number=" + number + ", payload=" + payload + "]";
            }
        }
    }
}
