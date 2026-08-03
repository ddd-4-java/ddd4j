package io.ddd4j.sample.vertx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import io.ddd4j.vertx.Ddd4jVertxRuntime;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class VertxOrderRoutesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Vertx vertx;
    private Ddd4jVertxRuntime runtime;
    private HttpServer server;

    @BeforeEach
    void setUp() throws Exception {
        vertx = Vertx.vertx();
        runtime = Ddd4jVertxRuntime.create(vertx, List.of());
        runtime.start();
        InMemoryOrderAdapters adapters = new InMemoryOrderAdapters();
        OrderApplicationService applicationService = new OrderApplicationService(adapters, adapters, adapters,
                adapters, adapters);
        server = await(vertx.createHttpServer()
                .requestHandler(VertxOrderRoutes.router(vertx, applicationService)).listen(0));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (Objects.nonNull(server)) {
            await(server.close());
        }
        if (Objects.nonNull(runtime)) {
            runtime.close();
        }
        if (Objects.nonNull(vertx)) {
            await(vertx.close());
        }
    }

    @Test
    void shouldRunSharedOrderUseCasesThroughVertxHttp() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String baseUrl = "http://127.0.0.1:" + server.actualPort();
        String token = issueToken(client, baseUrl);
        HttpResponse<String> create = request(client, baseUrl, "POST", "/api/orders", token,
                "{\"orderNo\":\"VERTX-001\",\"buyerId\":\"buyer-1\",\"buyerName\":\"Alice\"}");
        assertThat(create.statusCode()).withFailMessage(create.body()).isEqualTo(201);
        String orderId = objectMapper.readTree(create.body()).path("data").path("id").asText();

        HttpResponse<String> line = request(client, baseUrl, "POST", "/api/orders/" + orderId + "/lines", token,
                "{\"goodsId\":\"goods-1\",\"goodsName\":\"DDD Book\",\"quantity\":2,\"unitPrice\":59.90}");
        assertThat(line.statusCode()).withFailMessage(line.body()).isEqualTo(200);
        assertThat(objectMapper.readTree(line.body()).path("data").path("totalAmount").decimalValue())
                .isEqualByComparingTo("119.80");

        HttpResponse<String> missingKey = request(client, baseUrl, "POST", "/api/orders/" + orderId + "/pay", token,
                null);
        assertThat(missingKey.statusCode()).withFailMessage(missingKey.body()).isEqualTo(400);

        HttpRequest payment = HttpRequest.newBuilder(URI.create(baseUrl + "/api/orders/" + orderId + "/pay"))
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "vertx-payment-001")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> paid = client.send(payment, HttpResponse.BodyHandlers.ofString());
        assertThat(paid.statusCode()).withFailMessage(paid.body()).isEqualTo(200);
        assertThat(objectMapper.readTree(paid.body()).path("data").path("status").asText()).isEqualTo("PAID");
    }

    private String issueToken(HttpClient client, String baseUrl) throws Exception {
        HttpResponse<String> response = request(client, baseUrl, "POST", "/api/auth/tokens/vertx-user", null, null);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
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

    private <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
}
