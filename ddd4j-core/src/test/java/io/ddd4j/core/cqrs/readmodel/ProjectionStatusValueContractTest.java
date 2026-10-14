package io.ddd4j.core.cqrs.readmodel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectionStatusValueContractTest {

    @Test
    void jsonRoundTripPreservesSixComponents() throws Exception {
        ProjectionStatus status = status();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        String json = mapper.writeValueAsString(status);

        assertThat(mapper.readTree(json).size()).isEqualTo(6);
        assertThat(mapper.readValue(json, ProjectionStatus.class)).isEqualTo(status);
    }

    @Test
    void timestampRoundTripPreservesInstant() throws Exception {
        ProjectionStatus status = new ProjectionStatus("orders", 42L, true,
                Instant.parse("2026-01-01T01:02:03Z"), 7, null);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        ProjectionStatus restored = mapper.readValue(mapper.writeValueAsString(status), ProjectionStatus.class);

        assertThat(restored).isEqualTo(status);
    }

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

    private static ProjectionStatus status() {
        return new ProjectionStatus("orders", 42L, true, null, 7, null);
    }
}
