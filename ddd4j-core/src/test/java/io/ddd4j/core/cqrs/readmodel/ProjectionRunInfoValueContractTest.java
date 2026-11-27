package io.ddd4j.core.cqrs.readmodel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通过 <code>Arrays.asList()</code>、<code>Instant.parse()</code>、<code>mapper.writeValueAsString()</code> 验证 <code>ProjectionRunInfoValueContract</code> 的运行行为。
 */
class ProjectionRunInfoValueContractTest {

    /**
     * 验证<code>mapper.readTree(json).size()</code> 等于 <code>3</code>，<code>mapper.readValue(json, ProjectionRunInfo.class)</code> 等于 <code>value</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
     */
    @Test
    void jsonRoundTripPreservesThreeComponents() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        for (ProjectionRunInfo value : Arrays.asList(
                new ProjectionRunInfo(Instant.parse("2026-01-01T01:02:03Z"), 7, null),
                new ProjectionRunInfo(null, 0, "failed"))) {
            String json = mapper.writeValueAsString(value);

            assertThat(mapper.readTree(json).size()).isEqualTo(3);
            assertThat(mapper.readValue(json, ProjectionRunInfo.class)).isEqualTo(value);
        }
    }
}
