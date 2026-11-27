package io.ddd4j.core.cqrs.readmodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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

    /** \u6784\u9020\u5668\u7528\u4f8b\uff1a\u53c2\u6570\u6821\u9a8c\u3001\u8d1f\u6570\u62d2\u7edd\u3001\u7a7a\u5217\u8868\u8bed\u4e49\u4e0e\u9632\u5fa1\u6027\u62f7\u8d1d\u3002 */
    @Nested
    @DisplayName("构造器")
    class Constructor {

        /** \u9a8c\u8bc1<code>chunk.getEvents()</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"e1"</code>\u3001<code>"e2"</code>\u3001<code>"e3"</code>\uff0c<code>chunk.getNextEventNumber()</code> \u7b49\u4e8e <code>5L</code>\u3002 */
        @Test
        void 构造器_正常事件与位置_应保存事件并返回位置() {
            List<String> events = List.of("e1", "e2", "e3");

            EventChunk<String> chunk = new EventChunk<>(events, 5);

            assertThat(chunk.getEvents()).containsExactly("e1", "e2", "e3");
            assertThat(chunk.getNextEventNumber()).isEqualTo(5L);
        }

        /** \u9a8c\u8bc1<code>chunk.getNextEventNumber()</code>\u4e3a\u96f6\u3002 */
        @Test
        void 构造器_nextEventNumber为零_应允许() {
            EventChunk<String> chunk = new EventChunk<>(List.of("e1"), 0);

            assertThat(chunk.getNextEventNumber()).isZero();
        }

        /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
        @Test
        void 构造器_nextEventNumber为负数_应抛IllegalArgumentException() {
            assertThatThrownBy(() -> new EventChunk<>(List.of("e1"), -1))
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

        /** \u9a8c\u8bc1<code>chunk.getEvents()</code>\u4e3a\u7a7a\uff0c<code>chunk.hasEvents()</code>\u4e3a\u5047\u3002 */
        @Test
        void 构造器_events为空列表_应创建空chunk且hasEvents为false() {
            EventChunk<String> chunk = new EventChunk<>(List.of(), 0);

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

    /** empty \u5de5\u5382\u65b9\u6cd5\u7528\u4f8b\uff1a\u8fd4\u56de\u4e0d\u542b\u4e8b\u4ef6\u7684 chunk \u5e76\u4fdd\u7559\u4f20\u5165\u7684\u4e0b\u4e00\u4e2a\u4e8b\u4ef6\u53f7\u3002 */
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

    /** \u4e8b\u4ef6\u67e5\u8be2 API \u7528\u4f8b\uff1ahasEvents \u5224\u7a7a\u3001getEvents \u4e0d\u53ef\u53d8\u89c6\u56fe\u4e0e\u4e0b\u4e00\u4e2a\u4e8b\u4ef6\u53f7\u56de\u8bfb\u3002 */
    @Nested
    @DisplayName("hasEvents / getEvents")
    class QueryApi {

        /** \u9a8c\u8bc1<code>chunk.hasEvents()</code>\u4e3a\u771f\u3002 */
        @Test
        void hasEvents_事件非空_应返回true() {
            EventChunk<String> chunk = new EventChunk<>(List.of("e1"), 1);

            assertThat(chunk.hasEvents()).isTrue();
        }

        /** \u9a8c\u8bc1<code>chunk.getEvents().add("e3")</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\u3002 */
        @Test
        void getEvents_返回不可变List_修改应抛UnsupportedOperationException() {
            EventChunk<String> chunk = new EventChunk<>(List.of("e1", "e2"), 2);

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
            EventChunk<String> chunk = new EventChunk<>(List.of("e1"), 99L);

            assertThat(chunk.getNextEventNumber()).isEqualTo(99L);
        }
    }
}
