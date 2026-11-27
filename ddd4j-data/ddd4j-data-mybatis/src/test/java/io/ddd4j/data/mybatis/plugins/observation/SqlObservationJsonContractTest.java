package io.ddd4j.data.mybatis.plugins.observation;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** \u9a8c\u8bc1 <code>SqlObservationJsonContract</code> \u5728 <code>jsonRoundTrip</code> \u7b49\u573a\u666f\u4e2d\u7684\u884c\u4e3a\u3002 */
class SqlObservationJsonContractTest {

    /**
     * \u9a8c\u8bc1<code>restored</code> \u7b49\u4e8e <code>original</code>\uff0c<code>restored.getSortedParams()</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>"PAID"</code>\u3001<code>"2024"</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
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

    /**
     * \u9a8c\u8bc1<code>restored</code> \u7b49\u4e8e <code>original</code>\uff0c<code>mapper.readTree(json).has("error")</code>\u4e3a\u771f\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    @Test
    void jsonRoundTripWithOnlyExplicitJsonProperties() throws Exception {
        SqlObservation original = new SqlObservation("OrderMapper.listPaid", "SELECT * FROM orders",
                Arrays.asList("PAID", "2024"), 8_000_000L, null);
        ObjectMapper mapper = JsonMapper.builder()
                .addMixIn(SqlObservation.class, ExplicitProperties.class)
                .build();

        String json = mapper.writeValueAsString(original);
        SqlObservation restored = mapper.readValue(json, SqlObservation.class);

        assertThat(restored).isEqualTo(original);
        assertThat(mapper.readTree(json).has("error")).isTrue();
    }

    /** \u6a21\u62df\u663e\u5f0f\u58f0\u660e\u7684\u6846\u67b6\u914d\u7f6e\uff0c\u4ee5\u68c0\u9a8c\u81ea\u52a8\u88c5\u914d\u56de\u9000\u8fb9\u754c\u3002 */
    @JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE)
    private abstract static class ExplicitProperties {
    }
}
