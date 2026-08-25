package io.ddd4j.sample.helidon.cqrs;

import io.helidon.microprofile.tests.junit5.HelidonTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Helidon 订单 CQRS 集成测试。
 */
@HelidonTest
@DisplayName("Helidon Order CQRS 集成测试")
class HelidonOrderCqrsIT {

    @Inject
    private WebTarget webTarget;

    @Test
    @DisplayName("POST /orders 创建订单 -> 返回 201 + orderId")
    void createOrder_thenProjection() {
        Response response = webTarget.path("/orders")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Map.of("orderNo", "ORD-001", "buyerId", "B001", "buyerName", "Alice")));

        assertThat(response.getStatus()).isEqualTo(201);
        Map<String, Object> body = response.readEntity(Map.class);
        assertThat((Boolean) body.get("success")).isTrue();
        assertThat((String) body.get("orderId")).isNotEmpty();
    }

    @Test
    @DisplayName("GET /orders/{id} 查询读模型 -> 返回正确数据")
    void getOrder_fromReadModel() {
        Response createResp = webTarget.path("/orders")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Map.of("orderNo", "ORD-002", "buyerId", "B002", "buyerName", "Bob")));
        String orderId = (String) createResp.readEntity(Map.class).get("orderId");

        // 触发投影
        HelidonCqrsApplication.VIEW_MANAGER.triggerOnce();

        Response getResp = webTarget.path("/orders/" + orderId)
                .request(MediaType.APPLICATION_JSON)
                .get();
        assertThat(getResp.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("幂等性：同一 orderNo 重复创建 -> 返回 409 Conflict")
    void createOrder_idempotent() {
        webTarget.path("/orders")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Map.of("orderNo", "ORD-003", "buyerId", "B003", "buyerName", "Charlie")));

        Response response = webTarget.path("/orders")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Map.of("orderNo", "ORD-003", "buyerId", "B003", "buyerName", "Charlie")));

        assertThat(response.getStatus()).isEqualTo(409);
    }
}
