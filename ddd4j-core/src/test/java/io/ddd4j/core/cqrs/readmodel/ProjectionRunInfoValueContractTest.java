package io.ddd4j.core.cqrs.readmodel;

import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectionRunInfoValueContractTest {

    @Test
    void jsonRoundTripPreservesThreeComponents() throws Exception {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        for (ProjectionRunInfo value : Arrays.asList(
                new ProjectionRunInfo(Instant.parse("2026-01-01T01:02:03Z"), 7, null),
                new ProjectionRunInfo(null, 0, "failed"))) {
            String json = mapper.writeValueAsString(value);

            assertThat(mapper.readTree(json).size()).isEqualTo(3);
            assertThat(mapper.readValue(json, ProjectionRunInfo.class)).isEqualTo(value);
        }
    }
}
