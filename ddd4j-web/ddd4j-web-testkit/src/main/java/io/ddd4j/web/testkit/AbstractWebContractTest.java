package io.ddd4j.web.testkit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 所有 Web 适配器必须继承的基础成功、异常和鉴权响应契约。
 */
public abstract class AbstractWebContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    protected abstract WebContractClient client();

    @Test
    void shouldExposeStableSuccessEnvelope() throws Exception {
        WebContractResponse response = client().request("GET", "/contract/success", Map.of(), null);
        JsonNode body = objectMapper.readTree(response.body());

        assertThat(response.status()).isEqualTo(200);
        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("data").isMissingNode()).isFalse();
    }

    @Test
    void shouldExposeStableUnauthorizedEnvelope() throws Exception {
        WebContractResponse response = client().request("GET", "/contract/protected", Map.of(), null);
        JsonNode body = objectMapper.readTree(response.body());

        assertThat(response.status()).isEqualTo(401);
        assertThat(body.path("code").asInt()).isEqualTo(401);
    }
}
