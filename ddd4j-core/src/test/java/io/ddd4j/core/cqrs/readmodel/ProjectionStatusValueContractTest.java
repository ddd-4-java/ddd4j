package io.ddd4j.core.cqrs.readmodel;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/** \u901a\u8fc7 <code>status()</code>\u3001<code>JsonMapper.builder()</code>\u3001<code>mapper.writeValueAsString()</code> \u9a8c\u8bc1 <code>ProjectionStatusValueContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class ProjectionStatusValueContractTest {

    /**
     * \u521b\u5efa <code>new ProjectionStatus</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new ProjectionStatus("orders", 42L, true, null, 7, null)</code>
     */
    private static ProjectionStatus status() {
        return new ProjectionStatus("orders", 42L, true, null, 7, null);
    }

    /**
     * \u9a8c\u8bc1<code>mapper.readTree(json).size()</code> \u7b49\u4e8e <code>6</code>\uff0c<code>mapper.readValue(json, ProjectionStatus.class)</code> \u7b49\u4e8e <code>status</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
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
     * \u9a8c\u8bc1<code>restored</code> \u7b49\u4e8e <code>status</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    @Test
    void timestampRoundTripPreservesInstant() throws Exception {
        ProjectionStatus status = new ProjectionStatus("orders", 42L, true,
                Instant.parse("2026-01-01T01:02:03Z"), 7, null);
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

        ProjectionStatus restored = mapper.readValue(mapper.writeValueAsString(status), ProjectionStatus.class);

        assertThat(restored).isEqualTo(status);
    }

    /** \u9a8c\u8bc1<code>status.hashCode()</code> \u7b49\u4e8e <code>expected</code>\u3002 */
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
