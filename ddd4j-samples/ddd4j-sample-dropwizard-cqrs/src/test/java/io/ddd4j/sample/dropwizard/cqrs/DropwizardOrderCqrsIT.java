package io.ddd4j.sample.dropwizard.cqrs;

import io.ddd4j.sample.dropwizard.cqrs.web.OrderResource;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dropwizard 订单 CQRS HTTP 集成测试。
 *
 * <p>使用 {@link ResourceExtension} + {@link DropwizardExtensionsSupport}
 * 通过 HTTP 层测试 REST 端点，而非直接调用组件方法。
 *
 * <p>{@link OrderResource} 访问 {@link DropwizardCqrsApplication} 中的
 * 共享静态组件，测试通过 {@code @BeforeEach} 重置读模型状态。
 */
@ExtendWith(DropwizardExtensionsSupport.class)
@DisplayName("Dropwizard Order CQRS 集成测试")
class DropwizardOrderCqrsIT {

    private static final ResourceExtension RESOURCES = ResourceExtension.builder()
            .addResource(new OrderResource())
            .build();

    @BeforeEach
    void cleanUp() {
        DropwizardCqrsApplication.READ_VIEW.clear();
    }

    @Test
    @DisplayName("POST /orders 创建订单 -> 返回 201 + orderId")
    void createOrder_returns201() {
        Response response = RESOURCES.target("/orders")
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
        Response createResp = RESOURCES.target("/orders")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Map.of("orderNo", "ORD-002", "buyerId", "B002", "buyerName", "Bob")));
        String orderId = (String) createResp.readEntity(Map.class).get("orderId");

        // 触发投影（使用 Application 共享的 ViewManager）
        DropwizardCqrsApplication.VIEW_MANAGER.triggerOnce();

        Response getResp = RESOURCES.target("/orders/" + orderId)
                .request(MediaType.APPLICATION_JSON)
                .get();
        assertThat(getResp.getStatus()).isEqualTo(200);
        Map<String, Object> entity = getResp.readEntity(Map.class);
        assertThat((String) entity.get("orderNo")).isEqualTo("ORD-002");
        assertThat((String) entity.get("buyerName")).isEqualTo("Bob");
    }

    @Test
    @DisplayName("幂等性：同一 orderNo 重复创建 -> 返回 409 Conflict")
    void createOrder_idempotent() {
        RESOURCES.target("/orders")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Map.of("orderNo", "ORD-003", "buyerId", "B003", "buyerName", "Charlie")));

        Response response = RESOURCES.target("/orders")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Map.of("orderNo", "ORD-003", "buyerId", "B003", "buyerName", "Charlie")));

        assertThat(response.getStatus()).isEqualTo(409);
    }
}
