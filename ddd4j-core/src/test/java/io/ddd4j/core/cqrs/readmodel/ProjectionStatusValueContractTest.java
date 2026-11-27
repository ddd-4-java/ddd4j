package io.ddd4j.core.cqrs.readmodel;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通过 <code>status()</code>、<code>JsonMapper.builder()</code>、<code>mapper.writeValueAsString()</code> 验证 <code>ProjectionStatusValueContract</code> 的运行行为。
 */
class ProjectionStatusValueContractTest {

    /**
     * 创建 <code>new ProjectionStatus</code> 供当前测试路径调用。
     *
     * @return 当前测试使用的结果，取自 <code>new ProjectionStatus("orders", 42L, true, null, 7, null)</code>
     */
    private static ProjectionStatus status() {
        return new ProjectionStatus("orders", 42L, true, null, 7, null);
    }

    /**
     * 验证<code>mapper.readTree(json).size()</code> 等于 <code>6</code>，<code>mapper.readValue(json, ProjectionStatus.class)</code> 等于 <code>status</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
     */
    @Test
    void jsonRoundTripPreservesSixComponents() throws Exception {
        ProjectionStatus status = status();
        JsonMapper mapper = JsonMapper.builder().build();

        String json = mapper.writeValueAsString(status);

        assertThat(mapper.readTree(json).size()).isEqualTo(6);
        assertThat(mapper.readValue(json, ProjectionStatus.class)).isEqualTo(status);
    }

    /**
     * 验证<code>restored</code> 等于 <code>status</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
     */
    @Test
    void timestampRoundTripPreservesInstant() throws Exception {
        ProjectionStatus status = new ProjectionStatus("orders", 42L, true,
                Instant.parse("2026-01-01T01:02:03Z"), 7, null);
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

        ProjectionStatus restored = mapper.readValue(mapper.writeValueAsString(status), ProjectionStatus.class);

        assertThat(restored).isEqualTo(status);
    }

    /**
     * 验证<code>status.hashCode()</code> 等于 <code>expected</code>。
     */
    @Test
    void hashAndDescriptionMatchAcrossLines() {
        ProjectionStatus status = status();
        int expected = 0;
        expected = 31 * expected + Objects.hashCode("orders");
        expected = 31 * expected + Long.hashCode(42L);
        expected = 31 * expected + Boolean.hashCode(true);
        expected = 31 * expected + Objects.hashCode(null);
        expected = 31 * expected + Integer.hashCode(7);
        expected = 31 * expected + Objects.hashCode(null);

        assertThat(status.hashCode()).isEqualTo(expected);
        assertThat(status.toString()).isEqualTo(
                "ProjectionStatus[streamId=orders, nextEventNumber=42, running=true, "
                        + "lastRunAt=null, lastEventCount=7, lastError=null]");
    }
}
