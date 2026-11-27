package io.ddd4j.web.testkit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通过 <code>Collections.singletonMap()</code>、<code>Collections.singletonList()</code>、<code>response.hashCode()</code> 验证 <code>WebContractResponseValueContract</code> 的运行行为。
 */
class WebContractResponseValueContractTest {

    /**
     * 验证<code>response.hashCode()</code> 等于 <code>997929782</code>，<code>response.toString()</code> 等于 <code>"WebContractResponse[status=201, headers={X-Request-Id=[req-1]}, body=ok]"</code>。
     */
    @Test
    void responseValueHasTheSameHashAndTextOnEveryLine() {
        Map<String, List<String>> headers = Collections.singletonMap(
                "X-Request-Id", Collections.singletonList("req-1"));
        WebContractResponse response = new WebContractResponse(201, headers, "ok");

        assertThat(response.hashCode()).isEqualTo(997929782);
        assertThat(response.toString()).isEqualTo(
                "WebContractResponse[status=201, headers={X-Request-Id=[req-1]}, body=ok]");
    }

    /**
     * 验证<code>restored</code> 等于 <code>response</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
     */
    @Test
    void responseJsonRoundTripPreservesItsValue() throws Exception {
        Map<String, List<String>> headers = Collections.singletonMap(
                "X-Request-Id", Collections.singletonList("req-1"));
        WebContractResponse response = new WebContractResponse(201, headers, "ok");
        ObjectMapper mapper = new ObjectMapper();

        WebContractResponse restored = mapper.readValue(
                mapper.writeValueAsString(response), WebContractResponse.class);

        assertThat(restored).isEqualTo(response);
    }
}
