package io.ddd4j.sample.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.event.EntityId;
import io.ddd4j.core.ddd.event.NoopDomainEventPublisher;
import io.ddd4j.sample.order.domain.event.TradeOrderPaymentConfirmed;
import java.util.ArrayList;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.adapter.http.CatalogCqrs;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.sample.order.domain.gateway.DuplicateTradeKey;
import io.ddd4j.sample.order.domain.model.GoodsSku;
import io.ddd4j.sample.order.domain.model.GoodsSpu;
import io.ddd4j.sample.order.infrastructure.CatalogDatabase;
import java.util.NoSuchElementException;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Testcontainers
class CatalogTradeTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    @Test
    void databaseTranslatesDuplicateSkuAndRollsBackItsParent() {
        String tenant = UUID.randomUUID().toString().substring(0, 16);
        var source = new UnpooledDataSource(MYSQL.getDriverClassName(), MYSQL.getJdbcUrl(),
                MYSQL.getUsername(), MYSQL.getPassword());
        var transactions = CatalogDatabase.open(source);
        var sku = GoodsSku.unlimited(tenant, "DUP", "Edition", 500L);
        var one = GoodsSpu.listPackage(tenant, "FIRST", "Book", 7L, sku);
        var duplicate = GoodsSpu.listPackage(tenant, "SECOND", "Another book", 7L, sku);
        transactions.execute(tenant, session -> session.goods().save(one));
        assertThrows(DuplicateTradeKey.class,
                () -> transactions.execute(tenant, session -> session.goods().save(duplicate)));
        assertThrows(NoSuchElementException.class,
                () -> transactions.execute(tenant, session -> session.goods().findByCode("SECOND")));
    }

    @Test
    void startupAndRestartPreserveTenantScopedOrderSnapshots() throws Exception {
        String tenant = UUID.randomUUID().toString().substring(0, 16);
        String goods = """
                {"spuName":"Lunch","skuCode":"S1","skuName":"Weekday","salePriceCent":1250,"shopId":7}
                """;
        String order = """
                {"shopId":7,"items":[{"skuCode":"S1","quantity":2},{"skuCode":"S1","quantity":1}]}
                """;
        var source = new UnpooledDataSource(MYSQL.getDriverClassName(), MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        CatalogApi api = CatalogRuntime.start(source, new NoopDomainEventPublisher());
        assertSchema(source);
        assertEquals(201, api.createGoods(tenant, "G1", goods).getStatus());
        assertEquals(409, api.createGoods(tenant, "another", goods).getStatus());
        assertEquals(404, api.goods(tenant, "another").getStatus());
        assertEquals(404, api.goods(tenant + "x", "G1").getStatus());
        var product = api.goods(tenant, "G1").getPayload();
        assertEquals(tenant, product.path("tenantId").asText());
        assertEquals("G1", product.path("spuCode").asText());
        assertEquals("PACKAGE", product.path("productKind").asText());
        assertEquals("APPROVED", product.path("reviewState").asText());
        assertEquals("UNLIMITED", product.at("/skus/0/stockMode").asText());
        assertEquals(1250, product.at("/skus/0/salePriceCent").asLong());
        assertEquals(404, api.placeOrder(tenant, "O1", order.replace(":7", ":8")).getStatus());
        assertEquals(404, api.order(tenant, "O1").getStatus());
        assertEquals(201, api.placeOrder(tenant, "O1", order).getStatus());
        assertEquals(409, api.placeOrder(tenant, "O1", order).getStatus());
        try (var c = source.getConnection();
             var statement = c.prepareStatement("UPDATE goods_sku SET sale_price_cent=8 WHERE tenant_id=? AND sku_code='S1'")) {
            statement.setString(1, tenant);
            assertEquals(1, statement.executeUpdate());
        }
        CatalogApi restarted = CatalogRuntime.start(source, new NoopDomainEventPublisher());
        var json = new ObjectMapper().readTree(restarted.order(tenant, "O1").getBody());
        assertEquals(3750, json.get("grossAmountCent").asLong());
        assertEquals("GROUP_BUY", json.path("orderKind").asText());
        assertEquals("AWAITING_PAYMENT", json.path("orderState").asText());
        assertEquals("PENDING", json.path("fulfillmentState").asText());
        assertEquals("NONE", json.path("refundState").asText());
        assertEquals("Lunch", json.at("/items/0/productNameSnapshot").asText());
        assertEquals(json.path("id").asLong(), json.at("/items/0/orderId").asLong());
        assertEquals(2, json.get("items").size());
        assertEquals(1250, json.at("/items/0/unitPriceCent").asLong());
        assertEquals(404, restarted.order(tenant + "x", "O1").getStatus());
    }

    @Test
    void catalogCommandsWriteBeforeProjectionAndProjectionRebuildsFromTradeTables() {
        String tenant = UUID.randomUUID().toString().substring(0, 16);
        var source = new UnpooledDataSource(MYSQL.getDriverClassName(), MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        CatalogApi api = CatalogRuntime.start(source, new NoopDomainEventPublisher());
        var cqrs = new CatalogCqrs(api, new DefaultCommandBus(List.of(CatalogCqrs.executor(api))));
        assertEquals(201, cqrs.createGoods(tenant, "G1",
                """
                {"spuName":"Lunch","skuCode":"S1","skuName":"Weekday","salePriceCent":1250,"shopId":7}
                """).getStatus());
        assertEquals(201, cqrs.placeOrder(tenant, "O1", """
                {"shopId":7,"items":[{"skuCode":"S1","quantity":2}]}
                """).getStatus());
        assertEquals(404, cqrs.order(tenant, "O1").getStatus());
        assertEquals(404, cqrs.project(tenant + "x", "O1").getStatus());
        var projected = cqrs.project(tenant, "O1");
        assertEquals(200, projected.getStatus());
        assertEquals(api.order(tenant, "O1").getPayload(), projected.getPayload());
        assertEquals(2500, cqrs.order(tenant, "O1").getPayload().path("grossAmountCent").asInt());

        CatalogApi restarted = CatalogRuntime.start(source, new NoopDomainEventPublisher());
        var rebuilt = new CatalogCqrs(restarted, new DefaultCommandBus(List.of(CatalogCqrs.executor(restarted))));
        assertEquals(404, rebuilt.order(tenant, "O1").getStatus());
        assertEquals(200, rebuilt.project(tenant, "O1").getStatus());
        assertEquals(cqrs.order(tenant, "O1").getPayload(), rebuilt.order(tenant, "O1").getPayload());
    }
    @Test
    void paymentUpdatesTradeFactsAndPreservesTheOriginalSnapshot() {
        var source = new UnpooledDataSource(MYSQL.getDriverClassName(), MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        var committed = CatalogDatabase.open(source);
        List<DomainEvent<?>> published = new ArrayList<>();
        CatalogApi api = CatalogRuntime.start(source, new DomainEventPublisher() {
            @Override
            public <ID extends EntityId> void publish(DomainEvent<ID> event) {
                if (event instanceof TradeOrderPaymentConfirmed payment) {
                    var order = committed.execute(payment.getTenantId(),
                            session -> session.orders().findByOrderNo(payment.getOrderNo()));
                    assertEquals("CONFIRMED", order.getOrderState());
                }
                published.add(event);
            }
        });
        String tenant = UUID.randomUUID().toString();
        tenant = tenant.substring(0, 24);
        assertEquals(201, api.createGoods(tenant, "G1", """
                {"spuName":"Lunch","skuCode":"S1","skuName":"Weekday","salePriceCent":1250,"shopId":7}
                """).getStatus());
        assertEquals(201, api.placeOrder(tenant, "O1", """
                {"shopId":7,"items":[{"skuCode":"S1","quantity":2}]}
                """).getStatus());
        assertEquals(404, api.confirmPayment(tenant + "x", "O1", 2500L).getStatus());
        assertEquals(409, api.confirmPayment(tenant, "O1", 2499L).getStatus());
        assertEquals("AWAITING_PAYMENT", api.order(tenant, "O1").getPayload().path("orderState").asText());
        var result = api.confirmPayment(tenant, "O1", 2500L);
        assertEquals(200, result.getStatus());
        assertEquals("CONFIRMED", result.getPayload().path("orderState").asText());
        assertEquals(2500, result.getPayload().path("paidAmountCent").asLong());
        assertFalse(result.getPayload().path("paidTime").isNull());
        assertEquals(1250, result.getPayload().at("/items/0/unitPriceCent").asLong());
        assertEquals(result.getPayload(), api.confirmPayment(tenant, "O1", 2500L).getPayload());
        assertEquals(409, api.confirmPayment(tenant, "O1", 2501L).getStatus());
        assertEquals(2, published.size());
        assertEquals("TradeOrderPlaced", published.get(0).getClass().getSimpleName());
        var event = (TradeOrderPaymentConfirmed) published.get(1);
        assertEquals(tenant, event.getTenantId());
        assertEquals("O1", event.getOrderNo());
        assertEquals(2500, event.getPaidAmountCent());
    }

    @Test
    void cancellationAndFulfillmentUseTheSeparateTradeStates() {
        var source = new UnpooledDataSource(MYSQL.getDriverClassName(), MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        CatalogApi api = CatalogRuntime.start(source, new NoopDomainEventPublisher());
        String tenant = UUID.randomUUID().toString().substring(0, 20);
        assertEquals(201, api.createGoods(tenant, "G1", """
                {"spuName":"Lunch","skuCode":"S1","skuName":"Weekday","salePriceCent":1250,"shopId":7}
                """).getStatus());
        String purchase = """
                {"shopId":7,"items":[{"skuCode":"S1","quantity":1}]}
                """;
        assertEquals(201, api.placeOrder(tenant, "CANCEL", purchase).getStatus());
        assertEquals(409, api.fulfillOrder(tenant, "CANCEL").getStatus());
        var cancelled = api.cancelOrder(tenant, "CANCEL");
        assertEquals("CANCELLED", cancelled.getPayload().path("orderState").asText());
        assertEquals("NONE", cancelled.getPayload().path("refundState").asText());
        assertEquals(409, api.confirmPayment(tenant, "CANCEL", 1250L).getStatus());

        assertEquals(201, api.placeOrder(tenant, "FULFILL", purchase).getStatus());
        assertEquals(200, api.confirmPayment(tenant, "FULFILL", 1250L).getStatus());
        var fulfilled = api.fulfillOrder(tenant, "FULFILL");
        assertEquals("CONFIRMED", fulfilled.getPayload().path("orderState").asText());
        assertEquals("FULFILLED", fulfilled.getPayload().path("fulfillmentState").asText());
        assertEquals(409, api.cancelOrder(tenant, "FULFILL").getStatus());
        assertEquals(404, api.fulfillOrder(tenant + "x", "FULFILL").getStatus());
    }

    private static void assertSchema(UnpooledDataSource source) throws Exception {
        Map<String, Integer> counts = new HashMap<>();
        try (var connection = source.getConnection();
             var statement = connection.prepareStatement("""
                     SELECT TABLE_NAME, COLUMN_COMMENT
                     FROM information_schema.COLUMNS
                     WHERE TABLE_SCHEMA = DATABASE()
                     """);
             var result = statement.executeQuery()) {
            while (result.next()) {
                assertFalse(result.getString("COLUMN_COMMENT").isBlank(), result.getString("TABLE_NAME"));
                counts.merge(result.getString("TABLE_NAME"), 1, Integer::sum);
            }
        }
        assertEquals(Map.of("goods_spu", 17, "goods_sku", 14, "trade_order", 20, "trade_order_item", 14), counts);
    }

}
