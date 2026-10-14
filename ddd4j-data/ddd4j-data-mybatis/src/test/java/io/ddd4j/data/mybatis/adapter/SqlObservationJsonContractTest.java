package io.ddd4j.data.mybatis.adapter;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SqlObservationJsonContractTest {

    @Test
    void jsonRoundTripPreservesObservationFields() throws Exception {
        SqlObservation original = new SqlObservation(
                "OrderMapper.listPaid", "SELECT * FROM orders", Arrays.asList("PAID", "2024"),
                8_000_000L, null);
        ObjectMapper mapper = new ObjectMapper();

        SqlObservation restored = mapper.readValue(mapper.writeValueAsString(original), SqlObservation.class);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.getSortedParams()).containsExactly("PAID", "2024");
    }
}
