package io.ddd4j.core.cqrs.readmodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link NoopEventChunkReader} 单元测试。
 *
 * <p>验证空读取器始终返回空 chunk，且把传入的 fromEventNumber 原样作为下一个事件号返回，
 * 从而不会推进任何投影位置。
 *
 * @author PartMe.AI
 */
@DisplayName("NoopEventChunkReader")
class NoopEventChunkReaderTest {

    /** \u9a8c\u8bc1<code>chunk</code>\u975e\u7a7a\uff0c<code>chunk.hasEvents()</code>\u4e3a\u5047\uff0c<code>chunk.getEvents()</code>\u4e3a\u7a7a\u3002 */
    @Test
    void read_应返回空chunk() {
        NoopEventChunkReader<String> reader = new NoopEventChunkReader<>();

        EventChunk<String> chunk = reader.read("person-list", 0, 100, List.of("created"));

        assertThat(chunk).isNotNull();
        assertThat(chunk.hasEvents()).isFalse();
        assertThat(chunk.getEvents()).isEmpty();
    }

    /** \u9a8c\u8bc1<code>chunk.getNextEventNumber()</code> \u7b49\u4e8e <code>42L</code>\u3002 */
    @Test
    void read_nextEventNumber应等于传入的fromEventNumber() {
        NoopEventChunkReader<String> reader = new NoopEventChunkReader<>();

        EventChunk<String> chunk = reader.read("person-list", 42, 100, List.of("created"));

        assertThat(chunk.getNextEventNumber()).isEqualTo(42L);
    }

    /** \u9a8c\u8bc1<code>chunk.getNextEventNumber()</code>\u4e3a\u96f6\uff0c<code>chunk.hasEvents()</code>\u4e3a\u5047\u3002 */
    @Test
    void read_fromEventNumber为零时_应返回nextEventNumber为零的空chunk() {
        NoopEventChunkReader<String> reader = new NoopEventChunkReader<>();

        EventChunk<String> chunk = reader.read("person-list", 0, 100, null);

        assertThat(chunk.getNextEventNumber()).isZero();
        assertThat(chunk.hasEvents()).isFalse();
    }
}
