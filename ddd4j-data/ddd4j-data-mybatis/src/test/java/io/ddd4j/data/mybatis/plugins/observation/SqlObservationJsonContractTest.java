package io.ddd4j.data.mybatis.plugins.observation;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 <code>SqlObservationJsonContract</code> 在 <code>jsonRoundTrip</code> 等场景中的行为。
 */
class SqlObservationJsonContractTest {

    /**
     * 验证<code>restored</code> 等于 <code>original</code>，<code>restored.getSortedParams()</code> 按顺序仅包含 <code>"PAID"</code>、<code>"2024"</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
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
     * 验证<code>restored</code> 等于 <code>original</code>，<code>mapper.readTree(json).has("error")</code>为真。
     *
     * @throws Exception 处理测试数据的序列化时失败
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

    /**
     * 模拟显式声明的框架配置，以检验自动装配回退边界。
     */
    @JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE)
    private abstract static class ExplicitProperties {
    }
}
