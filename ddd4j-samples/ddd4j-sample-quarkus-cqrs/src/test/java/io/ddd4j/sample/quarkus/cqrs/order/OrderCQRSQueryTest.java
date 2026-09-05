package io.ddd4j.sample.quarkus.cqrs.order;

import io.ddd4j.sample.quarkus.cqrs.order.application.AddOrderLineCommand;
import io.ddd4j.sample.quarkus.cqrs.order.application.CreateOrderCommand;
import io.ddd4j.sample.quarkus.cqrs.order.application.OrderApplicationService;
import io.ddd4j.sample.quarkus.cqrs.order.domain.model.Order;
import io.ddd4j.sample.quarkus.cqrs.order.domain.model.OrderStatus;
import io.ddd4j.sample.quarkus.cqrs.order.domain.repository.OrderRepository;
import io.ddd4j.sample.quarkus.cqrs.order.infrastructure.InMemoryOrderRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * 订单 CQRS 读写分离集成测试。
 *
 * <p>演示：
 * <ul>
 *   <li>命令端：{@code /orders}（POST 创建、添加订单行、支付、发货、取消、批量取消）</li>
 *   <li>查询端：{@code /query/orders}（基础查询）+ {@code /api/orders/query/*}（缓存增强）</li>
 *   <li>缓存命中：CQRS 读侧优先缓存</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@QuarkusTest
class OrderCQRSQueryTest {

    @Inject
    OrderApplicationService applicationService;

    @Inject
    OrderRepository repository;

    @Inject
    InMemoryOrderRepository inMemoryRepository;

    // ========== 命令端应用服务层 ==========

    @Test
    void shouldCreateDraftThroughCommandSide() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-CQRS-1001", "BUYER-CQRS-1", "Alice"));
        assertThat(draft.id()).isNotBlank();
        assertThat(draft.status()).isEqualTo(OrderStatus.DRAFT);
        assertThat(repository.findById(draft.id())).isPresent();
    }

    @Test
    void shouldWalkFullOrderStateMachine() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-CQRS-1002", "BUYER-CQRS-2", "Bob"));
        applicationService.addLine(new AddOrderLineCommand(
                draft.id(), "SKU-1", "Item", 2, new BigDecimal("49.90")));
        Order paid = applicationService.pay(draft.id());
        assertThat(paid.status()).isEqualTo(OrderStatus.PAID);

        Order shipped = applicationService.ship(draft.id());
        assertThat(shipped.status()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void shouldCancelOrderThroughCommandSide() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-CQRS-1003", "BUYER-CQRS-3", "Carol"));
        Order cancelled = applicationService.cancel(draft.id());
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    // ========== 命令端 REST 端点 ==========

    @Test
    void shouldCreateOrderViaCommandRest() {
        Map<String, Object> body = new HashMap<>();
        body.put("orderNo", "ORDER-CQRS-REST-1");
        body.put("buyerId", "BUYER-CQRS-REST-1");
        body.put("buyerName", "REST-CQRS");

        given().contentType(ContentType.JSON).body(body)
                .when().post("/orders")
                .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.id", notNullValue())
                .body("data.status", equalTo("DRAFT"));
    }

    @Test
    void shouldGetOrderByIdViaCommandRest() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-CQRS-REST-2", "BUYER-REST-2", "REST"));
        given()
                .when().get("/orders/{id}", draft.id())
                .then()
                .statusCode(200)
                .body("data.id", equalTo(draft.id()));
    }

    @Test
    void shouldFindByOrderNoViaCommandRest() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-CQRS-REST-3", "BUYER-REST-3", "X"));
        given()
                .when().get("/orders/orderNo/{no}", draft.orderNo())
                .then()
                .statusCode(200)
                .body("data.orderNo", equalTo(draft.orderNo()));
    }

    @Test
    void shouldAddLineViaCommandRest() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-CQRS-REST-4", "BUYER-REST-4", "Y"));
        Map<String, Object> line = new HashMap<>();
        line.put("goodsId", "SKU-REST");
        line.put("goodsName", "REST Item");
        line.put("quantity", 5);
        line.put("unitPrice", 9.99);
        given().contentType(ContentType.JSON).body(line)
                .when().post("/orders/{id}/lines", draft.id())
                .then()
                .statusCode(200)
                .body("data.lines.size()", equalTo(1));
    }

    @Test
    void shouldPayOrderViaCommandRest() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-CQRS-REST-5", "BUYER-REST-5", "Z"));
        applicationService.addLine(new AddOrderLineCommand(
                draft.id(), "S", "Z", 1, new BigDecimal("10")));
        given().urlEncodingEnabled(false)
                .when().post("/orders/{id}:pay", draft.id())
                .then()
                .statusCode(200)
                .body("data.status", equalTo("PAID"));
    }

    @Test
    void shouldShipOrderViaCommandRest() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-CQRS-REST-6", "BUYER-REST-6", "S"));
        applicationService.addLine(new AddOrderLineCommand(
                draft.id(), "S", "S", 1, new BigDecimal("10")));
        applicationService.pay(draft.id());
        given().urlEncodingEnabled(false)
                .when().post("/orders/{id}:ship", draft.id())
                .then()
                .statusCode(200)
                .body("data.status", equalTo("SHIPPED"));
    }

    @Test
    void shouldCancelOrderViaCommandRest() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-CQRS-REST-7", "BUYER-REST-7", "C"));
        given().urlEncodingEnabled(false)
                .when().post("/orders/{id}:cancel", draft.id())
                .then()
                .statusCode(200)
                .body("data.status", equalTo("CANCELLED"));
    }

    @Test
    void shouldCancelAllDraftsViaCommandRest() {
        String buyer = "BUYER-CQRS-BATCH";
        applicationService.createDraft(new CreateOrderCommand("BATCH-Q1", buyer, "P"));
        applicationService.createDraft(new CreateOrderCommand("BATCH-Q2", buyer, "P"));

        Map<String, Object> body = new HashMap<>();
        body.put("buyerId", buyer);
        given().contentType(ContentType.JSON).body(body)
                .when().post("/orders/cancel-all")
                .then()
                .statusCode(200)
                .body("data", greaterThanOrEqualTo(2));
    }

    // ========== 基础查询端（/query/orders） ==========

    @Test
    void shouldQueryOrderViaBaseQueryEndpoint() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-CQRS-BQ-1", "BUYER-BQ-1", "X"));
        given()
                .when().get("/query/orders/{id}", draft.id())
                .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.id", equalTo(draft.id()));
    }

    @Test
    void shouldReturn404ForMissingOrderOnQueryEndpoint() {
        given()
                .when().get("/query/orders/{id}", "no-such-cqrs-order")
                .then()
                .statusCode(200)
                .body("code", equalTo(404));
    }

    @Test
    void shouldListAllOrdersOnQueryEndpoint() {
        applicationService.createDraft(
                new CreateOrderCommand("ORDER-LIST-CQRS-A", "BUYER-LIST-A", "AA"));
        given()
                .when().get("/query/orders")
                .then().statusCode(200)
                .body("code", equalTo(0));
    }

    @Test
    void shouldListOrdersByPaidStatus() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-LIST-PAID", "BUYER-LIST-P", "P"));
        applicationService.addLine(new AddOrderLineCommand(
                draft.id(), "SKU", "I", 1, new BigDecimal("10")));
        applicationService.pay(draft.id());

        given()
                .queryParam("status", "PAID")
                .when().get("/query/orders")
                .then().statusCode(200)
                .body("code", equalTo(0));
    }

    @Test
    void shouldListOrdersByCancelledStatus() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-LIST-CANCEL", "BUYER-LIST-C", "C"));
        applicationService.cancel(draft.id());

        given()
                .queryParam("status", "CANCELLED")
                .when().get("/query/orders")
                .then().statusCode(200)
                .body("code", equalTo(0));
    }

    @Test
    void shouldCountOrdersViaQueryEndpoint() {
        applicationService.createDraft(
                new CreateOrderCommand("ORDER-CQRS-CNT", "BUYER-CNT", "CC"));
        given()
                .when().get("/query/orders/count")
                .then().statusCode(200)
                .body("code", equalTo(0));
    }

    // ========== 缓存增强查询端（/api/orders/query/*） ==========

    @Test
    void shouldHitOrderStatsViaCachedQueryEndpoint() {
        applicationService.createDraft(
                new CreateOrderCommand("ORDER-CQRS-STATS-1", "BUYER-STATS-1", "S"));
        applicationService.createDraft(
                new CreateOrderCommand("ORDER-CQRS-STATS-2", "BUYER-STATS-2", "S"));

        given()
                .when().get("/api/orders/query/stats")
                .then()
                .statusCode(200)
                .body("code", equalTo(0));
    }

    @Test
    void shouldGetBuyerOrderCountViaCachedQueryEndpoint() {
        String buyer = "BUYER-CQRS-COUNT";
        applicationService.createDraft(new CreateOrderCommand("X1", buyer, "P"));
        applicationService.createDraft(new CreateOrderCommand("X2", buyer, "P"));

        given()
                .when().get("/api/orders/query/buyer/{buyerId}/count", buyer)
                .then()
                .statusCode(200)
                .body("data.buyerId", equalTo(buyer))
                .body("data.count", greaterThanOrEqualTo(2));
    }

    @Test
    void shouldHitZeroBuyerCountForNonExistingBuyer() {
        given()
                .when().get("/api/orders/query/buyer/{buyerId}/count", "ghost-buyer-" + System.nanoTime())
                .then()
                .statusCode(200)
                .body("data.count", equalTo(0));
    }

    @Test
    void shouldGetOrderDetailViaCachePreferredEndpoint() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-CQRS-DETAIL", "BUYER-DETAIL", "D"));
        given()
                .when().get("/api/orders/query/detail/{id}", draft.id())
                .then()
                .statusCode(200)
                .body("data.id", equalTo(draft.id()));
    }

    @Test
    void shouldReturn404ForDetailViaCacheEndpoint() {
        given()
                .when().get("/api/orders/query/detail/{id}", "no-such-detail")
                .then()
                .statusCode(200)
                .body("code", equalTo(404));
    }

    @Test
    void shouldListOrdersViaCachePreferredEndpoint() {
        given()
                .queryParam("page", 1)
                .queryParam("pageSize", 10)
                .when().get("/api/orders/query/list")
                .then()
                .statusCode(200)
                .body("data.page", equalTo(1))
                .body("data.pageSize", equalTo(10));
    }

    @Test
    void shouldListWithDefaultPaginationWhenNotProvided() {
        given()
                .when().get("/api/orders/query/list")
                .then()
                .statusCode(200)
                .body("data.page", equalTo(1))
                .body("data.pageSize", equalTo(10));
    }

    // ========== 完整 CQRS 流程 ==========

    @Test
    void shouldSupportFullCqrsRoundTrip() {
        // 命令端写
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-CQRS-FULL", "BUYER-FULL", "F"));
        applicationService.addLine(new AddOrderLineCommand(
                draft.id(), "SKU-F", "F-Item", 1, new BigDecimal("99")));
        applicationService.pay(draft.id());

        // 基础查询端读
        given()
                .when().get("/query/orders/{id}", draft.id())
                .then()
                .statusCode(200)
                .body("data.status", equalTo("PAID"));

        // 缓存增强端读（应命中缓存）
        given()
                .when().get("/api/orders/query/detail/{id}", draft.id())
                .then()
                .statusCode(200)
                .body("data.status", equalTo("PAID"));
    }

    @Test
    void shouldCacheCancelledOrderThenEvict() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-CQRS-CANCEL-CACHE", "BUYER-CC", "CC"));
        // 写入缓存（applicationService 同步刷新）
        applicationService.cancel(draft.id());
        given()
                .when().get("/api/orders/query/detail/{id}", draft.id())
                .then()
                .statusCode(200)
                .body("data.status", equalTo("CANCELLED"));
    }

    @Test
    void shouldResolveOrderByOrderNoOnCommandSide() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-CQRS-RESOLVE", "BUYER-RES", "R"));
        given()
                .when().get("/orders/orderNo/{no}", draft.orderNo())
                .then().statusCode(200)
                .body("data.orderNo", equalTo(draft.orderNo()));
    }

    @Test
    void shouldFindByOrderNoViaRepository() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-CQRS-FIND", "BUYER-FIND", "F"));
        assertThat(repository.findByOrderNo(draft.orderNo())).isPresent();
    }

    @Test
    void shouldFindByStatusViaInMemoryRepository() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-CQRS-FBS", "BUYER-FBS", "F"));
        applicationService.cancel(draft.id());
        assertThat(inMemoryRepository.findByStatus(OrderStatus.CANCELLED))
                .anyMatch(o -> o.id().equals(draft.id()));
    }

    @Test
    void shouldReturnRepoCountAtLeastOne() {
        applicationService.createDraft(
                new CreateOrderCommand("ORDER-CQRS-CNT-1", "BUYER-CNT-1", "C"));
        assertThat(inMemoryRepository.count()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void shouldReadAcrossQueryEndpointAndCacheEndpoint() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-CQRS-CROSS", "BUYER-X", "X"));
        // 缓存查询命中写入
        given().when().get("/api/orders/query/detail/{id}", draft.id()).then().statusCode(200);
        // 基础查询也命中
        given().when().get("/query/orders/{id}", draft.id()).then().statusCode(200);
    }
}
