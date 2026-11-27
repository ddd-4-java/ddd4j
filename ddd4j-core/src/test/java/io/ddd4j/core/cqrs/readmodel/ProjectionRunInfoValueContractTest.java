package io.ddd4j.core.cqrs.readmodel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** \u901a\u8fc7 <code>Arrays.asList()</code>\u3001<code>Instant.parse()</code>\u3001<code>mapper.writeValueAsString()</code> \u9a8c\u8bc1 <code>ProjectionRunInfoValueContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class ProjectionRunInfoValueContractTest {

    /**
     * \u9a8c\u8bc1<code>mapper.readTree(json).size()</code> \u7b49\u4e8e <code>3</code>\uff0c<code>mapper.readValue(json, ProjectionRunInfo.class)</code> \u7b49\u4e8e <code>value</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
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
