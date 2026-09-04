package io.ddd4j.sample.vertx.cqrs;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vert.x 订单 CQRS 集成测试。
 */
@ExtendWith(VertxExtension.class)
@DisplayName("Vert.x Order CQRS 集成测试")
class VertxOrderCqrsIT {

    private int port;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        VertxCqrsApplication.READ_VIEW.clear();
        Router router = VertxCqrsApplication.createRouter(vertx);
        vertx.createHttpServer().requestHandler(router).listen(0)
                .onSuccess(server -> {
                    port = server.actualPort();
                    testContext.completeNow();
                })
                .onFailure(testContext::failNow);
    }

    @Test
    @DisplayName("POST /orders 创建订单 -> 返回 201 + orderId")
    void createOrder_thenProjection() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/orders"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"orderNo\":\"ORD-001\",\"buyerId\":\"B001\",\"buyerName\":\"Alice\"}"))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(201);
        JsonObject body = new JsonObject(response.body());
        assertThat(body.getBoolean("success")).isTrue();
        assertThat(body.getString("orderId")).isNotEmpty();
    }

    @Test
    @DisplayName("GET /orders/{id} 查询读模型 -> 返回正确数据")
    void getOrder_fromReadModel() throws Exception {
        // 先创建订单
        HttpRequest createReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/orders"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"orderNo\":\"ORD-002\",\"buyerId\":\"B002\",\"buyerName\":\"Bob\"}"))
                .build();
        HttpResponse<String> createResp = httpClient.send(createReq, HttpResponse.BodyHandlers.ofString());
        String orderId = new JsonObject(createResp.body()).getString("orderId");

        // 触发投影
        VertxCqrsApplication.VIEW_MANAGER.triggerOnce();

        // 查询读模型
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/orders/" + orderId))
                .GET()
                .build();
        HttpResponse<String> getResp = httpClient.send(getReq, HttpResponse.BodyHandlers.ofString());

        assertThat(getResp.statusCode()).isEqualTo(200);
        JsonObject body = new JsonObject(getResp.body());
        assertThat(body.getString("orderNo")).isEqualTo("ORD-002");
        assertThat(body.getString("buyerName")).isEqualTo("Bob");
    }

    @Test
    @DisplayName("幂等性：同一 orderNo 重复创建 -> 返回 409 Conflict")
    void createOrder_idempotent() throws Exception {
        HttpRequest first = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/orders"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"orderNo\":\"ORD-003\",\"buyerId\":\"B003\",\"buyerName\":\"Charlie\"}"))
                .build();
        httpClient.send(first, HttpResponse.BodyHandlers.ofString());

        HttpRequest second = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/orders"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"orderNo\":\"ORD-003\",\"buyerId\":\"B003\",\"buyerName\":\"Charlie\"}"))
                .build();
        HttpResponse<String> response = httpClient.send(second, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(409);
    }
}
