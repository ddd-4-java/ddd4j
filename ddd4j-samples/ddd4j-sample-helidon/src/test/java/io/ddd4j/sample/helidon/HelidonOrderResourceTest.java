package io.ddd4j.sample.helidon;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@HelidonTest
@AddBean(HelidonOrderApplication.class)
@AddBean(HelidonOrderBeans.class)
@AddConfig(key = "ddd4j.web.public-paths", value = "/api/auth/**")
class HelidonOrderResourceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    private WebTarget target;

    @Test
    void shouldRunSharedOrderUseCasesThroughHelidonHttp() throws Exception {
        String token = issueToken();
        Response create = request("POST", "/api/orders", token,
                "{\"orderNo\":\"HELIDON-001\",\"buyerId\":\"buyer-1\",\"buyerName\":\"Alice\"}");
        String createBody = readAndClose(create);
        assertThat(create.getStatus()).withFailMessage(createBody).isEqualTo(201);
        String orderId = objectMapper.readTree(createBody).path("data").path("id").asText();

        Response line = request("POST", "/api/orders/" + orderId + "/lines", token,
                "{\"goodsId\":\"goods-1\",\"goodsName\":\"DDD Book\",\"quantity\":2,\"unitPrice\":59.90}");
        String lineBody = readAndClose(line);
        assertThat(line.getStatus()).withFailMessage(lineBody).isEqualTo(200);
        assertThat(objectMapper.readTree(lineBody).path("data").path("totalAmount").decimalValue())
                .isEqualByComparingTo("119.80");

        Response missingKey = request("POST", "/api/orders/" + orderId + "/pay", token, null);
        String missingKeyBody = readAndClose(missingKey);
        assertThat(missingKey.getStatus()).withFailMessage(missingKeyBody).isEqualTo(400);

        Invocation.Builder payment = target.path("/api/orders/" + orderId + "/pay").request()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "helidon-payment-001");
        Response paid = payment.post(Entity.entity("", MediaType.APPLICATION_JSON_TYPE));
        String paidBody = readAndClose(paid);
        assertThat(paid.getStatus()).withFailMessage(paidBody).isEqualTo(200);
        assertThat(objectMapper.readTree(paidBody).path("data").path("status").asText()).isEqualTo("PAID");
    }

    private String issueToken() throws Exception {
        Response response = request("POST", "/api/auth/tokens/helidon-user", null, null);
        String body = readAndClose(response);
        assertThat(response.getStatus()).withFailMessage(body).isEqualTo(200);
        JsonNode payload = objectMapper.readTree(body);
        String token = payload.path("data").path("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private Response request(String method, String path, String token, String body) {
        Invocation.Builder builder = target.path(path).request();
        if (Objects.nonNull(token)) {
            builder.header("Authorization", "Bearer " + token);
        }
        return Objects.isNull(body)
                ? builder.method(method)
                : builder.method(method, Entity.entity(body, MediaType.APPLICATION_JSON_TYPE));
    }

    private String readAndClose(Response response) {
        try (response) {
            return response.readEntity(String.class);
        }
    }
}
