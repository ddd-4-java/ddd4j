package io.ddd4j.sample.micronaut;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class MicronautOrderControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRunSharedOrderUseCasesThroughMicronautHttp() throws Exception {
        Map<String, Object> properties = Map.of(
                "micronaut.server.port", -1,
                "ddd4j.web.public-paths", List.of("/api/auth/**")
        );
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, properties)) {
            HttpClient client = HttpClient.newHttpClient();
            String baseUrl = "http://127.0.0.1:" + server.getPort();
            String token = issueToken(client, baseUrl);
            HttpResponse<String> create = request(client, baseUrl, "POST", "/api/orders", token,
                    "{\"orderNo\":\"MICRONAUT-001\",\"buyerId\":\"buyer-1\",\"buyerName\":\"Alice\"}");
            assertThat(create.statusCode()).withFailMessage(create.body()).isEqualTo(201);
            String orderId = objectMapper.readTree(create.body()).path("data").path("id").asText();

            HttpResponse<String> line = request(client, baseUrl, "POST", "/api/orders/" + orderId + "/lines", token,
                    "{\"goodsId\":\"goods-1\",\"goodsName\":\"DDD Book\",\"quantity\":2,\"unitPrice\":59.90}");
            assertThat(line.statusCode()).isEqualTo(200);
            assertThat(objectMapper.readTree(line.body()).path("data").path("totalAmount").decimalValue())
                    .isEqualByComparingTo("119.80");

            HttpResponse<String> paid = request(client, baseUrl, "POST", "/api/orders/" + orderId + "/pay", token, null);
            assertThat(paid.statusCode()).isEqualTo(400);

            HttpRequest payRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/api/orders/" + orderId + "/pay"))
                    .header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", "micronaut-payment-001")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> successfulPayment = client.send(payRequest, HttpResponse.BodyHandlers.ofString());
            assertThat(successfulPayment.statusCode()).withFailMessage(successfulPayment.body()).isEqualTo(200);
            assertThat(objectMapper.readTree(successfulPayment.body()).path("data").path("status").asText())
                    .isEqualTo("PAID");
        }
    }

    private String issueToken(HttpClient client, String baseUrl) throws Exception {
        HttpResponse<String> response = request(client, baseUrl, "POST", "/api/auth/tokens/micronaut-user", null, null);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(response.statusCode()).isEqualTo(200);
        String token = body.path("data").path("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private HttpResponse<String> request(HttpClient client, String baseUrl, String method, String path, String token,
                                         String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path));
        if (Objects.nonNull(token)) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (Objects.nonNull(body)) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
