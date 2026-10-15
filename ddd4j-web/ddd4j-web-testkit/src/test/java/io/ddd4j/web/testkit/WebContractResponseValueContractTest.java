package io.ddd4j.web.testkit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WebContractResponseValueContractTest {

    @Test
    void responseValueHasTheSameHashAndTextOnEveryLine() {
        Map<String, List<String>> headers = Collections.singletonMap(
                "X-Request-Id", Collections.singletonList("req-1"));
        WebContractResponse response = new WebContractResponse(201, headers, "ok");

        assertThat(response.hashCode()).isEqualTo(997929782);
        assertThat(response.toString()).isEqualTo(
                "WebContractResponse[status=201, headers={X-Request-Id=[req-1]}, body=ok]");
    }

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
