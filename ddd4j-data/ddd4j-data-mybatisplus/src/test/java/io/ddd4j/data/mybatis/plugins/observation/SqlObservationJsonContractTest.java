package io.ddd4j.data.mybatis.plugins.observation;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlObservationJsonContractTest {

    @Test
    void jsonRoundTripPreservesObservationFields() throws Exception {
        SqlObservation original = new SqlObservation(
                "OrderMapper.select", "SELECT * FROM orders", 3_500_000L, null);
        ObjectMapper mapper = new ObjectMapper();

        SqlObservation restored = mapper.readValue(mapper.writeValueAsString(original), SqlObservation.class);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.elapsedMillis()).isEqualTo(3L);
    }

    @Test
    void jsonRoundTripWithOnlyExplicitJsonProperties() throws Exception {
        SqlObservation original = new SqlObservation("OrderMapper.select", "SELECT * FROM orders", 3_500_000L, null);
        ObjectMapper mapper = JsonMapper.builder()
                .addMixIn(SqlObservation.class, ExplicitProperties.class)
                .build();

        String json = mapper.writeValueAsString(original);
        SqlObservation restored = mapper.readValue(json, SqlObservation.class);

        assertThat(restored).isEqualTo(original);
        assertThat(mapper.readTree(json).has("error")).isTrue();
    }

    @JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE)
    private abstract static class ExplicitProperties {
    }
}
