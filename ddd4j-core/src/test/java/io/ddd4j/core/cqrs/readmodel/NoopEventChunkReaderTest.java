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

    @Test
    void read_应返回空chunk() {
        NoopEventChunkReader<String> reader = new NoopEventChunkReader<>();

        EventChunk<String> chunk = reader.read("person-list", 0, 100, List.of("created"));

        assertThat(chunk).isNotNull();
        assertThat(chunk.hasEvents()).isFalse();
        assertThat(chunk.getEvents()).isEmpty();
    }

    @Test
    void read_nextEventNumber应等于传入的fromEventNumber() {
        NoopEventChunkReader<String> reader = new NoopEventChunkReader<>();

        EventChunk<String> chunk = reader.read("person-list", 42, 100, List.of("created"));

        assertThat(chunk.getNextEventNumber()).isEqualTo(42L);
    }

    @Test
    void read_fromEventNumber为零时_应返回nextEventNumber为零的空chunk() {
        NoopEventChunkReader<String> reader = new NoopEventChunkReader<>();

        EventChunk<String> chunk = reader.read("person-list", 0, 100, null);

        assertThat(chunk.getNextEventNumber()).isZero();
        assertThat(chunk.hasEvents()).isFalse();
    }
}
