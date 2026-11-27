package io.ddd4j.sample.order;

import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.app.projection.CatalogOrderView;
import io.ddd4j.sample.order.app.projection.ProjectedTradeOrderQuery;
import io.ddd4j.sample.order.client.query.TradeOrderQuery;
import io.ddd4j.sample.order.domain.model.*;
import io.ddd4j.sample.order.infrastructure.CatalogDatabase;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证商品交易示例的层间制品边界、聚合约束和投影并发顺序。
 */
class CatalogArchitectureTest {
    /**
     * 构造架构测试所用的单行订单明细。
     *
     * @return 数量为一的示例订单明细
     */
    private static TradeOrderItem line() {
        return TradeOrderItem.fromOffer("tenant", 1, new GoodsSkuOffer(5L, "Book", "Edition", 1200L), 1L);
    }

    /**
     * 等待并发测试中的指定阶段，在超时或中断时使断言失败。
     *
     * @param latch 用来同步两个并发测试阶段的门闩
     * @throws AssertionError 等待门闩超时或当前线程被中断
     */
    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    /**
     * 验证 HTTP 适配层和 JDBC 持久化层来自不同构建制品。
     */
    @Test
    void jdbcAndHttpAreDistinctArtifacts() {
        assertNotEquals(CatalogApi.class.getProtectionDomain().getCodeSource().getLocation(),
                CatalogDatabase.class.getProtectionDomain().getCodeSource().getLocation());
    }

    /**
     * 验证商品或订单聚合拒绝属于其他租户的子对象。
     */
    @Test
    void aggregatesRejectChildrenFromAnotherTenant() {
        var offer = new GoodsSkuOffer(5L, "Book", "Edition", 1200L);
        var line = TradeOrderItem.fromOffer("other-tenant", 1, offer, 1L);
        assertThrows(IllegalArgumentException.class,
                () -> TradeOrder.place("tenant", "O-1", 3L, List.of(line)));
        var sku = GoodsSku.unlimited("other-tenant", "S-1", "Edition", 1200L);
        assertThrows(IllegalArgumentException.class,
                () -> GoodsSpu.listPackage("tenant", "G-1", "Book", 3L, sku));
    }

    /**
     * 验证领域聚合未公开绕过校验入口的构建方法。
     */
    @Test
    void aggregateCannotBeBuiltWithoutItsValidatedEntryPoints() {
        assertThrows(NoSuchMethodException.class, () -> TradeOrder.class.getMethod("builder"));
        assertThrows(NoSuchMethodException.class, () -> GoodsSpu.class.getMethod("builder"));
        var invalidLine = TradeOrderItem.builder().tenantId("tenant").quantity(-1L).unitPriceCent(1200L).build();
        assertThrows(IllegalArgumentException.class,
                () -> TradeOrder.place("tenant", "O-1", 3L, List.of(invalidLine)));
    }

    /**
     * 验证还原的订单独立持有明细，调用方修改原始集合无法改变聚合。
     */
    @Test
    void restoredAggregateOwnsItsItemCollection() {
        var items = new ArrayList<>(List.of(line()));
        var order = TradeOrder.restore(1L, "tenant", "O-1", "GROUP_BUY", "VOUCHER", 3L, null,
                "AWAITING_PAYMENT", "PENDING", "NONE", null, null, 1200L, null,
                null, 0L, null, 0L, null, 0, items);
        items.clear();
        assertEquals(1, order.getItems().size());
        assertThrows(UnsupportedOperationException.class, () -> order.getItems().clear());
    }

    /**
     * 验证同一订单的第二次并发投影需等第一次发布后方可读取。
     *
     * @throws Exception 测试线程获取结果失败或等待超时
     */
    @Test
    void concurrentRebuildCannotReadTheSameKeyBeforeOlderRebuildPublishes() throws Exception {
        var pending = TradeOrder.place("tenant", "O-1", 3L, List.of(line()));
        var confirmed = pending.confirmPayment(1200L, LocalDateTime.of(2026, 1, 1, 0, 0));
        var persisted = new AtomicReference<>(pending);
        var reads = new AtomicInteger();
        var oldRead = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var newRead = new CountDownLatch(1);
        var secondRequest = new CountDownLatch(1);
        var view = new CatalogOrderView(query -> {
            var snapshot = persisted.get();
            if (reads.incrementAndGet() == 1) {
                oldRead.countDown();
                await(release);
            } else {
                newRead.countDown();
            }
            return snapshot;
        });

        var query = new TradeOrderQuery("tenant", "O-1");
        var workers = Executors.newFixedThreadPool(2);
        try {
            var older = workers.submit(() -> view.rebuild(query));
            await(oldRead);
            persisted.set(confirmed);
            var newer = workers.submit(() -> {
                secondRequest.countDown();
                return view.rebuild(query);
            });
            await(secondRequest);
            assertFalse(newRead.await(200, TimeUnit.MILLISECONDS));
            release.countDown();
            assertEquals("AWAITING_PAYMENT", older.get(5, TimeUnit.SECONDS).getOrderState());
            assertEquals("CONFIRMED", newer.get(5, TimeUnit.SECONDS).getOrderState());
            var result = view.handle(new ProjectedTradeOrderQuery("tenant", "O-1"));
            assertEquals("CONFIRMED", result.getOrderState());
            assertEquals(1200, result.getItems().get(0).getUnitPriceCent());
            assertThrows(UnsupportedOperationException.class, () -> result.getItems().clear());
        } finally {
            release.countDown();
            workers.shutdownNow();
        }
    }
}
