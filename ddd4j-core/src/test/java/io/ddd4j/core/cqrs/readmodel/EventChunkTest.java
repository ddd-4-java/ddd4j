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

    @Nested
    @DisplayName("构造器")
    class Constructor {

        @Test
        void 构造器_正常事件与位置_应保存事件并返回位置() {
            List<String> events = List.of("e1", "e2", "e3");

            EventChunk<String> chunk = new EventChunk<>(events, 5);

            assertThat(chunk.getEvents()).containsExactly("e1", "e2", "e3");
            assertThat(chunk.getNextEventNumber()).isEqualTo(5L);
        }

        @Test
        void 构造器_nextEventNumber为零_应允许() {
            EventChunk<String> chunk = new EventChunk<>(List.of("e1"), 0);

            assertThat(chunk.getNextEventNumber()).isZero();
        }

        @Test
        void 构造器_nextEventNumber为负数_应抛IllegalArgumentException() {
            assertThatThrownBy(() -> new EventChunk<>(List.of("e1"), -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nextEventNumber must not be negative");
        }

        @Test
        void 构造器_events为null_应抛NullPointerException() {
            assertThatThrownBy(() -> new EventChunk<String>(null, 1))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("events must not be null");
        }

        @Test
        void 构造器_events为空列表_应创建空chunk且hasEvents为false() {
            EventChunk<String> chunk = new EventChunk<>(List.of(), 0);

            assertThat(chunk.getEvents()).isEmpty();
            assertThat(chunk.hasEvents()).isFalse();
        }

        @Test
        void 构造器_传入可变List_应做防御性拷贝_原列表修改不影响chunk() {
            List<String> mutable = new ArrayList<>();
            mutable.add("e1");
            EventChunk<String> chunk = new EventChunk<>(mutable, 1);

            mutable.add("e2");

            assertThat(chunk.getEvents()).containsExactly("e1");
        }
    }

    @Nested
    @DisplayName("empty 工厂方法")
    class EmptyFactory {

        @Test
        void empty_应返回不含事件的chunk() {
            EventChunk<String> chunk = EventChunk.empty(10);

            assertThat(chunk.getEvents()).isEmpty();
            assertThat(chunk.hasEvents()).isFalse();
        }

        @Test
        void empty_应保留传入的nextEventNumber() {
            EventChunk<String> chunk = EventChunk.empty(42);

            assertThat(chunk.getNextEventNumber()).isEqualTo(42L);
        }

        @Test
        void empty_nextEventNumber为零_应允许() {
            EventChunk<String> chunk = EventChunk.empty(0);

            assertThat(chunk.getNextEventNumber()).isZero();
        }

        @Test
        void empty_nextEventNumber为负数_应抛IllegalArgumentException() {
            assertThatThrownBy(() -> EventChunk.empty(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("hasEvents / getEvents")
    class QueryApi {

        @Test
        void hasEvents_事件非空_应返回true() {
            EventChunk<String> chunk = new EventChunk<>(List.of("e1"), 1);

            assertThat(chunk.hasEvents()).isTrue();
        }

        @Test
        void getEvents_返回不可变List_修改应抛UnsupportedOperationException() {
            EventChunk<String> chunk = new EventChunk<>(List.of("e1", "e2"), 2);

            assertThatThrownBy(() -> chunk.getEvents().add("e3"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void getEvents_empty返回的List_修改应抛UnsupportedOperationException() {
            EventChunk<String> chunk = EventChunk.empty(0);

            assertThatThrownBy(() -> chunk.getEvents().add("x"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void getNextEventNumber_应返回构造时传入的值() {
            EventChunk<String> chunk = new EventChunk<>(List.of("e1"), 99L);

            assertThat(chunk.getNextEventNumber()).isEqualTo(99L);
        }
    }
}
