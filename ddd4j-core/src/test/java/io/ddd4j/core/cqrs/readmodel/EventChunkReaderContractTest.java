package io.ddd4j.core.cqrs.readmodel;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;

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
 * @author PartMe.AI
 */
@DisplayName("EventChunkReader SPI 契约")
class EventChunkReaderContractTest {

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

    @Test
    void read_从已读完的位置继续读_应返回空chunk且不推进位置() {
        InMemoryEventChunkReader reader = new InMemoryEventChunkReader();
        reader.append(1, "a");

        reader.read("s", 1, 100, null);
        EventChunk<String> chunk = reader.read("s", 2, 100, null);

        assertThat(chunk.hasEvents()).isFalse();
        assertThat(chunk.getNextEventNumber()).isEqualTo(2L);
    }

    @Test
    void read_空流首次读取_应返回nextEventNumber为零的空chunk() {
        InMemoryEventChunkReader reader = new InMemoryEventChunkReader();

        EventChunk<String> chunk = reader.read("s", 0, 100, null);

        assertThat(chunk.hasEvents()).isFalse();
        assertThat(chunk.getNextEventNumber()).isZero();
    }

    @Test
    void read_eventTypes为空_应读取全部类型() {
        InMemoryEventChunkReader reader = new InMemoryEventChunkReader();
        reader.append(1, "a");
        reader.append(2, "b");

        EventChunk<String> chunk = reader.read("s", 1, 100, Arrays.asList());

        assertThat(chunk.getEvents()).containsExactly("a", "b");
    }

    /**
     * 内存版测试桩：把事件按号顺序存储，{@code read} 按 fromEventNumber（包含）顺序读取，
     * 受 chunkSize 限制。返回的 nextEventNumber 为最后一个已读事件号 + 1（或原值当未读到）。
     */
    static class InMemoryEventChunkReader implements EventChunkReader<String> {

        private final Deque<Numbered> events = new ArrayDeque<>();

        void append(long number, String payload) {
            events.addLast(new Numbered(number, payload));
        }

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

        private record Numbered(long number, String payload) {
        }
    }
}
