package io.ddd4j.sample.quarkus;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.Contexts;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.event.MQEventPublisher;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.sample.quarkus.order.application.CreateOrderCommand;
import io.ddd4j.sample.quarkus.order.application.AddOrderLineCommand;
import io.ddd4j.sample.quarkus.order.application.OrderApplicationService;
import io.ddd4j.sample.quarkus.order.cache.OrderCacheService;
import io.ddd4j.sample.quarkus.order.domain.model.Order;
import io.ddd4j.sample.quarkus.order.domain.repository.OrderRepository;
import io.ddd4j.sample.quarkus.order.domain.service.OrderDomainService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * 订单资源 Quarkus 集成测试（完整覆盖）。
 *
 * <p>使用 {@link QuarkusTest} 启动完整的 Quarkus 应用上下文，
 * 包括 CDI 容器、JAX-RS 端点以及 ddd4j 启动期注入的 SPI。
 *
 * <h3>覆盖范围</h3>
 * <ul>
 *   <li>SPI 注入校验：通过 {@code Contexts.inject} 验证 4 个核心 SPI 已注入</li>
 *   <li>完整业务流：创建订单 → 添加行 → 支付 → 发货 → 取消</li>
 *   <li>REST 端点：{@code GET/POST /orders} 与所有子端点 + CQRS 查询侧</li>
 *   <li>批量取消、缓存命中、状态机非法转换</li>
 *   <li>统一响应 {@code R} 包装（code = 0）的验证</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@QuarkusTest
class OrderResourceTest {

    @Inject
    OrderRepository repository;

    @Inject
    OrderApplicationService applicationService;

    @Inject
    OrderCacheService cacheService;

    @Inject
    OrderDomainService domainService;

    @Inject
    DomainEventPublisher domainEventPublisher;

    @Inject
    MQEventPublisher mqEventPublisher;

    @Inject
    SubjectProvider subjectProvider;

    @Inject
    I18nProvider i18nProvider;

    // ========== SPI 注入 ==========

    @Test
    void shouldInjectFourCoreSpis() {
        Optional<DomainEventPublisher> publisher = Contexts.inject(
                SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class);
        Optional<MQEventPublisher> mq = Contexts.inject(
                SpiKeys.MQ_EVENT_PUBLISHER, MQEventPublisher.class);
        Optional<SubjectProvider> subject = Contexts.inject(
                SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class);
        Optional<I18nProvider> i18n = Contexts.inject(
                SpiKeys.I18N_PROVIDER, I18nProvider.class);

        assertThat(publisher).isPresent();
        assertThat(mq).isPresent();
        assertThat(subject).isPresent();
        assertThat(i18n).isPresent();
        assertThat(publisher.get()).isSameAs(domainEventPublisher);
        assertThat(mq.get()).isSameAs(mqEventPublisher);
        assertThat(subject.get()).isSameAs(subjectProvider);
        assertThat(i18n.get()).isSameAs(i18nProvider);
    }

    // ========== 业务层 ==========

    @Test
    void shouldCreateAddLineAndPayOrder() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-T-1001", "BUYER-T-1", "Alice"));

        assertThat(draft.id()).isNotBlank();
        assertThat(draft.status().name()).isEqualTo("DRAFT");
        assertThat(repository.findById(draft.id())).isPresent();

        Order withLine = applicationService.addLine(
                new AddOrderLineCommand(draft.id(), "SKU-1", "DDD Book", 2, new BigDecimal("39.80")));
        assertThat(withLine.lines()).hasSize(1);
        assertThat(withLine.totalAmount().amount()).isEqualByComparingTo("79.60");

        Order paid = applicationService.pay(draft.id());
        assertThat(paid.status().name()).isEqualTo("PAID");
        assertThat(paid.domainEvents())
                .anyMatch(event -> event.getClass().getSimpleName().equals("OrderPaidEvent"));
    }

    @Test
    void shouldFollowFullStateMachineFromShip() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-T-1002", "BUYER-T-2", "Bob"));
        applicationService.addLine(new AddOrderLineCommand(
                draft.id(), "SKU-X", "Item X", 1, new BigDecimal("10.00")));
        applicationService.pay(draft.id());
        Order shipped = applicationService.ship(draft.id());
        assertThat(shipped.status().name()).isEqualTo("SHIPPED");
    }

    @Test
    void shouldCancelDraftOrderBeforePaid() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-T-1003", "BUYER-T-3", "Carol"));
        Order cancelled = applicationService.cancel(draft.id());
        assertThat(cancelled.status().name()).isEqualTo("CANCELLED");
    }

    @Test
    void shouldRejectAddLineOnPaidOrder() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-T-1004", "BUYER-T-4", "Dave"));
        applicationService.addLine(new AddOrderLineCommand(
                draft.id(), "SKU-Y", "Item Y", 1, new BigDecimal("10.00")));
        applicationService.pay(draft.id());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> applicationService.addLine(new AddOrderLineCommand(
                        draft.id(), "SKU-Z", "Item Z", 1, new BigDecimal("10.00"))));
    }

    @Test
    void shouldRejectPayOnEmptyOrder() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-T-1005", "BUYER-T-5", "Eve"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> applicationService.pay(draft.id()));
    }

    @Test
    void shouldRejectCancelOnShippedOrder() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-T-1006", "BUYER-T-6", "Frank"));
        applicationService.addLine(new AddOrderLineCommand(
                draft.id(), "SKU-A", "Item A", 1, new BigDecimal("10.00")));
        applicationService.pay(draft.id());
        applicationService.ship(draft.id());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> applicationService.cancel(draft.id()));
    }

    @Test
    void shouldCacheOrderAfterCreate() {
        Order created = applicationService.createDraft(
                new CreateOrderCommand("ORDER-T-4001", "BUYER-T-4", "Gina"));
        assertThat(cacheService.getOrder(created.id())).isPresent();
        assertThat(cacheService.getOrder(created.id()).get().orderNo()).isEqualTo("ORDER-T-4001");
    }

    @Test
    void shouldRemoveOrderFromCacheAfterCancel() {
        Order created = applicationService.createDraft(
                new CreateOrderCommand("ORDER-T-4002", "BUYER-T-99", "Hans"));
        assertThat(cacheService.getOrder(created.id())).isPresent();
        applicationService.cancel(created.id());
        // OrderDomainService.cancel / applicationService.cancel 都会清除缓存
        assertThat(cacheService.getOrder(created.id())).isEmpty();
    }

    @Test
    void shouldQueryOrderByOrderNo() {
        Order created = applicationService.createDraft(
                new CreateOrderCommand("ORDER-T-4003", "BUYER-T-98", "Ivy"));
        Optional<Order> found = repository.findByOrderNo("ORDER-T-4003");
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(created.id());
    }

    @Test
    void shouldReturnEmptyWhenOrderNoMissing() {
        Optional<Order> found = repository.findByOrderNo("NOT-EXISTENT");
        assertThat(found).isEmpty();
    }

    @Test
    void shouldCountAllOrdersViaDomainService() {
        applicationService.createDraft(new CreateOrderCommand("ORDER-T-4004", "BUYER-X", "Jack"));
        int cancelled = domainService.cancelAllDraftsOf("BUYER-X");
        assertThat(cancelled).isGreaterThanOrEqualTo(1);
    }

    // ========== REST 端点 ==========

    @Test
    void shouldCreateOrderViaRest() {
        Map<String, Object> body = new HashMap<>();
        body.put("orderNo", "ORDER-T-2001");
        body.put("buyerId", "BUYER-T-2");
        body.put("buyerName", "Bob");

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/orders")
                .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.id", notNullValue())
                .body("data.orderNo", equalTo("ORDER-T-2001"))
                .body("data.status", equalTo("DRAFT"));
    }

    @Test
    void shouldGetOrderByIdViaRest() {
        Order created = applicationService.createDraft(
                new CreateOrderCommand("ORDER-T-3001", "BUYER-T-3", "Carol"));

        given()
                .when().get("/orders/{id}", created.id())
                .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.id", equalTo(created.id()))
                .body("data.orderNo", equalTo("ORDER-T-3001"));
    }

    @Test
    void shouldReturnNotFoundWhenOrderMissing() {
        given()
                .when().get("/orders/{id}", "non-existent-id")
                .then()
                .statusCode(200)
                .body("code", equalTo(404))
                .body("msg", notNullValue());
    }

    @Test
    void shouldFindByOrderNoViaRest() {
        Order created = applicationService.createDraft(
                new CreateOrderCommand("ORDER-T-3002", "BUYER-T-77", "Kim"));

        given()
                .when().get("/orders/orderNo/{no}", created.orderNo())
                .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.orderNo", equalTo(created.orderNo()));
    }

    @Test
    void shouldReturn404ForMissingOrderNo() {
        given()
                .when().get("/orders/orderNo/{no}", "NOT-PRESENT-ORDER")
                .then()
                .statusCode(200)
                .body("code", equalTo(404));
    }

    @Test
    void shouldAddLineViaRest() {
        Order created = applicationService.createDraft(
                new CreateOrderCommand("ORDER-T-3003", "BUYER-T-88", "Leo"));

        Map<String, Object> line = new HashMap<>();
        line.put("goodsId", "SKU-REST-1");
        line.put("goodsName", "REST Goods");
        line.put("quantity", 3);
        line.put("unitPrice", 99.99);

        given().contentType(ContentType.JSON).body(line)
                .when().post("/orders/{id}/lines", created.id())
                .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.lines.size()", equalTo(1))
                .body("data.lines[0].goodsId", equalTo("SKU-REST-1"));
    }

    @Test
    void shouldRejectAddLineForMissingOrder() {
        Map<String, Object> line = new HashMap<>();
        line.put("goodsId", "SKU-X");
        line.put("goodsName", "X");
        line.put("quantity", 1);
        line.put("unitPrice", 1.00);

        given().contentType(ContentType.JSON).body(line)
                .when().post("/orders/{id}/lines", "missing-order")
                .then()
                .statusCode(500);
    }

    @Test
    void shouldPayViaRest() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-T-3004", "BUYER-T-99", "Mia"));
        applicationService.addLine(new AddOrderLineCommand(
                draft.id(), "SKU", "Item", 1, new BigDecimal("10.00")));

        given()
                .when().post("/orders/{id}:pay", draft.id())
                .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.status", equalTo("PAID"));
    }

    @Test
    void shouldShipViaRest() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-T-3005", "BUYER-T-11", "Nina"));
        applicationService.addLine(new AddOrderLineCommand(
                draft.id(), "SKU", "Item", 1, new BigDecimal("10.00")));
        applicationService.pay(draft.id());

        given()
                .when().post("/orders/{id}:ship", draft.id())
                .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.status", equalTo("SHIPPED"));
    }

    @Test
    void shouldCancelViaRest() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-T-3006", "BUYER-T-12", "Oscar"));

        given()
                .when().post("/orders/{id}:cancel", draft.id())
                .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.status", equalTo("CANCELLED"));
    }

    @Test
    void shouldCancelAllDraftsViaRest() {
        String buyer = "BUYER-BATCH-1";
        applicationService.createDraft(new CreateOrderCommand("BATCH-1", buyer, "P1"));
        applicationService.createDraft(new CreateOrderCommand("BATCH-2", buyer, "P2"));

        Map<String, Object> body = new HashMap<>();
        body.put("buyerId", buyer);

        given().contentType(ContentType.JSON).body(body)
                .when().post("/orders/cancel-all")
                .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data", greaterThanOrEqualTo(2));
    }

    // ========== CQRS 查询端 ==========

    @Test
    void shouldQueryOrderViaCqrsEndpoint() {
        Order created = applicationService.createDraft(
                new CreateOrderCommand("ORDER-T-5001", "BUYER-T-5", "Eve"));

        given()
                .when().get("/query/orders/{id}", created.id())
                .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.id", equalTo(created.id()))
                .body("data.orderNo", equalTo("ORDER-T-5001"));
    }

    @Test
    void shouldReturn404WhenOrderMissingOnCqrs() {
        given()
                .when().get("/query/orders/{id}", "no-such-order")
                .then()
                .statusCode(200)
                .body("code", equalTo(404));
    }

    @Test
    void shouldListAllOrdersViaCqrs() {
        Order a = applicationService.createDraft(
                new CreateOrderCommand("ORDER-LIST-A", "BUYER-LIST-1", "AA"));
        applicationService.cancel(a.id());

        given()
                .when().get("/query/orders")
                .then()
                .statusCode(200)
                .body("code", equalTo(0));
    }

    @Test
    void shouldListOrdersByStatusViaCqrs() {
        Order paid = applicationService.createDraft(
                new CreateOrderCommand("ORDER-LIST-B", "BUYER-LIST-2", "BB"));
        applicationService.addLine(new AddOrderLineCommand(
                paid.id(), "SKU", "Item", 1, new BigDecimal("10.00")));
        applicationService.pay(paid.id());

        given()
                .queryParam("status", "PAID")
                .when().get("/query/orders")
                .then()
                .statusCode(200)
                .body("code", equalTo(0));
    }

    @Test
    void shouldReturnTotalCountViaCqrs() {
        applicationService.createDraft(
                new CreateOrderCommand("ORDER-CNT-1", "BUYER-CNT-1", "CC"));

        given()
                .when().get("/query/orders/count")
                .then()
                .statusCode(200)
                .body("code", equalTo(0));
    }

    @Test
    void shouldSupportFullCqrsFlow() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-T-6001", "BUYER-T-6", "Frank"));
        applicationService.addLine(new AddOrderLineCommand(
                draft.id(), "SKU-2", "Quarkus Guide", 1, new BigDecimal("59.90")));
        applicationService.pay(draft.id());

        given()
                .when().get("/query/orders/{id}", draft.id())
                .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.status", equalTo("PAID"))
                .body("data.lines.size()", equalTo(1));
    }

    @Test
    void shouldReturnCancelledOrderStatusViaCqrs() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-CANCEL-1", "BUYER-CANCEL-1", "GG"));
        applicationService.cancel(draft.id());

        given()
                .when().get("/query/orders/{id}", draft.id())
                .then()
                .statusCode(200)
                .body("data.status", equalTo("CANCELLED"));
    }

    @Test
    void shouldInvokeDomEventPublishers() {
        // 仅做 SPI 烟雾测试
        org.assertj.core.api.Assertions.assertThat(domainEventPublisher).isNotNull();
        org.assertj.core.api.Assertions.assertThat(mqEventPublisher).isNotNull();
    }

    @Test
    void shouldInvokeDomainServiceAuditOperator() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-AUDIT-1", "BUYER-AUDIT-1", "HM"));
        Optional<String> audit = domainService.auditOperator(draft.id());
        assertThat(audit).isPresent();
    }

    @Test
    void shouldReturnOrderForKnownIdOnRepository() {
        Order draft = applicationService.createDraft(
                new CreateOrderCommand("ORDER-REPO-1", "BUYER-REPO-1", "II"));
        Optional<Order> found = repository.findById(draft.id());
        assertThat(found).isPresent();
        assertThat(found.get().orderNo()).isEqualTo("ORDER-REPO-1");
    }
}
