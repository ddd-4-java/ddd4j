package io.ddd4j.web.testkit;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** \u901a\u8fc7 <code>Collections.singletonMap()</code>\u3001<code>Collections.singletonList()</code>\u3001<code>response.hashCode()</code> \u9a8c\u8bc1 <code>WebContractResponseValueContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class WebContractResponseValueContractTest {

    /** \u9a8c\u8bc1<code>response.hashCode()</code> \u7b49\u4e8e <code>997929782</code>\uff0c<code>response.toString()</code> \u7b49\u4e8e <code>"WebContractResponse[status=201, headers={X-Request-Id=[req-1]}, body=ok]"</code>\u3002 */
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
     * \u9a8c\u8bc1<code>restored</code> \u7b49\u4e8e <code>response</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
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
