package io.ddd4j.sample.order;

import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.data.mybatis.context.Ddd4jTenantContext;
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

/** \u4f7f\u7528\u771f\u5b9e MySQL \u9a8c\u8bc1\u5546\u54c1\u4ea4\u6613\u7684\u8868\u7ed3\u6784\u3001\u4e8b\u52a1\u3001\u79df\u6237\u9694\u79bb\u548c\u72b6\u6001\u6d41\u8f6c\u3002 */
@Testcontainers
class CatalogTradeTest {
    /** \u4e3a\u5546\u54c1\u4ea4\u6613\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u9694\u79bb\u6570\u636e\u5e93\u7684 MySQL \u5bb9\u5668\u3002 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    /** \u9a8c\u8bc1\u89c4\u683c\u552f\u4e00\u952e\u51b2\u7a81\u4f1a\u8f6c\u6210\u9886\u57df\u5f02\u5e38\uff0c\u5e76\u56de\u6eda\u540c\u4e8b\u52a1\u4e2d\u7684\u5546\u54c1\u8bb0\u5f55\u3002 */
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

    /**
     * \u9a8c\u8bc1\u91cd\u590d\u542f\u52a8\u4fdd\u7559\u8ba2\u5355\u5feb\u7167\uff0c\u4e14\u6309\u79df\u6237\u9694\u79bb\u540c\u4e1a\u52a1\u7f16\u53f7\u7684\u8ba2\u5355\u3002
     * @throws Exception \u6570\u636e\u5e93\u8868\u7ed3\u6784\u68c0\u67e5\u5931\u8d25
     */
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

    /** \u9a8c\u8bc1\u5199\u547d\u4ee4\u5148\u63d0\u4ea4\u4ea4\u6613\u4e8b\u5b9e\uff0c\u6295\u5f71\u53ef\u76f4\u63a5\u4ece\u4ea4\u6613\u8868\u91cd\u5efa\u5feb\u7167\u3002 */
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
    /** 验证支付更新交易状态和时间的同时保留商品下单快照。 */
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

    /** \u9a8c\u8bc1\u53d6\u6d88\u548c\u5c65\u7ea6\u5206\u522b\u4fee\u6539\u5bf9\u5e94\u72b6\u6001\u5b57\u6bb5\u3002 */
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

    /**
     * \u67e5\u8be2\u6570\u636e\u5e93\u5143\u6570\u636e\u5e76\u6838\u5bf9\u5546\u54c1\u4ea4\u6613\u56db\u5f20\u8868\u7684\u7ed3\u6784\u3002
     * @param source \u6307\u5411\u9694\u79bb\u6d4b\u8bd5 MySQL \u7684\u6570\u636e\u6e90
     * @throws Exception \u65e0\u6cd5\u8fde\u63a5\u6570\u636e\u5e93\u6216\u8bfb\u53d6\u8868\u7ed3\u6784
     */
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


    /**
     * 验证 MyBatis 事务把业务租户传给 DDD4J，并在异常后恢复请求租户。
     */
    @Test
    void catalogTransactionScopesAndRestoresTenant() {
        var source = new UnpooledDataSource(MYSQL.getDriverClassName(), MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        var transactions = CatalogDatabase.open(source);
        var context = new Ddd4jTenantContext();
        try (var request = ThreadContext.open(); var ignored = context.open("request-tenant")) {
            ThreadContext.set(ContextConstants.PREPARING_SQL, "caller-sql");
            var failure = assertThrows(IllegalStateException.class, () -> transactions.execute("trade-tenant", session -> {
                assertEquals("trade-tenant", context.getCurrentTenantId());
                assertFalse(session.goods().findById(Long.MAX_VALUE).isPresent());
                ThreadContext.set(ContextConstants.PREPARING_SQL, "transaction-local");
                throw new IllegalStateException("rollback");
            }));
            assertEquals("rollback", failure.getMessage());
            assertEquals("request-tenant", context.getCurrentTenantId());
            assertEquals("caller-sql", ThreadContext.get(ContextConstants.PREPARING_SQL));
        }
    }
}
