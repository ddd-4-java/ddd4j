package io.ddd4j.sample.micronaut.cqrs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.micronaut.cqrs.cqrs.ViewManager;
import io.ddd4j.sample.micronaut.cqrs.readmodel.OrderSummaryView;
import io.ddd4j.sample.micronaut.cqrs.readmodel.OrderSummaryViewEntity;
import io.ddd4j.sample.micronaut.cqrs.repository.EventSourcingOrderRepository;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Micronaut 订单 CQRS 集成测试：写侧（Command -> Aggregate -> EventStore）+ 读侧（Projection -> Query）。
 */
@MicronautTest
@DisplayName("Micronaut Order CQRS 集成测试")
class MicronautOrderCqrsIT {

    @Inject
    @Client("/")
    HttpClient httpClient;

    @Inject
    EventSourcingOrderRepository orderRepository;

    @Inject
    OrderSummaryView readView;

    @Inject
    ViewManager viewManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void cleanUp() {
        readView.clear();
    }

    @Test
    @DisplayName("POST /orders 创建订单 -> EventStore 有事件 + 读模型有记录")
    void createOrder_thenProjection() throws Exception {
        HttpRequest<Map<String, String>> request = HttpRequest.POST("/orders",
                Map.of("orderNo", "ORD-001", "buyerId", "B001", "buyerName", "Alice"));
        HttpResponse<String> response = httpClient.toBlocking().exchange(request, String.class);

        assertThat(response.code()).isEqualTo(201);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("success").asBoolean()).isTrue();
        assertThat(body.get("orderId").asText()).isNotEmpty();

        String orderId = body.get("orderId").asText();

        // 触发投影
        viewManager.triggerOnce();

        // 验证读模型
        OrderSummaryViewEntity entity = readView.findById(orderId);
        assertThat(entity).isNotNull();
        assertThat(entity.getOrderNo()).isEqualTo("ORD-001");
        assertThat(entity.getBuyerId()).isEqualTo("B001");
        assertThat(entity.getBuyerName()).isEqualTo("Alice");
        assertThat(entity.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("GET /orders/{id} 查询读模型 -> 返回正确数据")
    void getOrder_fromReadModel() throws Exception {
        // 先创建订单
        HttpRequest<Map<String, String>> createReq = HttpRequest.POST("/orders",
                Map.of("orderNo", "ORD-002", "buyerId", "B002", "buyerName", "Bob"));
        HttpResponse<String> createResp = httpClient.toBlocking().exchange(createReq, String.class);
        String orderId = objectMapper.readTree(createResp.body()).get("orderId").asText();

        // 触发投影
        viewManager.triggerOnce();

        // 查询读模型
        HttpRequest<Void> getReq = HttpRequest.GET("/orders/" + orderId);
        HttpResponse<String> getResp = httpClient.toBlocking().exchange(getReq, String.class);
        assertThat(getResp.code()).isEqualTo(200);
        JsonNode getOrder = objectMapper.readTree(getResp.body());
        assertThat(getOrder.get("orderNo").asText()).isEqualTo("ORD-002");
        assertThat(getOrder.get("buyerName").asText()).isEqualTo("Bob");
        assertThat(getOrder.get("status").asText()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("幂等性：同一 orderNo 重复创建 -> 返回 409 Conflict")
    void createOrder_idempotent() throws Exception {
        HttpRequest<Map<String, String>> first = HttpRequest.POST("/orders",
                Map.of("orderNo", "ORD-003", "buyerId", "B003", "buyerName", "Charlie"));
        httpClient.toBlocking().exchange(first, String.class);

        // Micronaut HTTP client throws on non-2xx; catch and verify 409
        HttpRequest<Map<String, String>> second = HttpRequest.POST("/orders",
                Map.of("orderNo", "ORD-003", "buyerId", "B003", "buyerName", "Charlie"));
        try {
            httpClient.toBlocking().exchange(second, String.class);
            fail("Expected 409 Conflict");
        } catch (HttpClientResponseException e) {
            assertThat(e.getStatus().getCode()).isEqualTo(409);
        }
    }
}
