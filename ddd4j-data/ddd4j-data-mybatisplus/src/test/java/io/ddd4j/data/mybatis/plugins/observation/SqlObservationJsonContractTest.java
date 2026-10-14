package io.ddd4j.data.mybatis.plugins.observation;

import tools.jackson.databind.ObjectMapper;
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
}
