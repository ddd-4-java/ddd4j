package io.ddd4j.sample.order;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.ddd4j.ddd.cola.rules.ColaDDDLayerRules;
import io.ddd4j.sample.order.domain.InvalidDomainDependency;
import io.ddd4j.sample.order.app.CatalogService;
import io.ddd4j.sample.order.adapter.http.CatalogApi;
import io.ddd4j.sample.order.app.projection.CatalogOrderView;
import io.ddd4j.sample.order.app.projection.ProjectedTradeOrderQuery;
import io.ddd4j.sample.order.client.query.TradeOrderQuery;
import io.ddd4j.sample.order.domain.model.GoodsSku;
import io.ddd4j.sample.order.domain.model.GoodsSkuOffer;
import io.ddd4j.sample.order.domain.model.GoodsSpu;
import io.ddd4j.sample.order.domain.model.TradeOrder;
import io.ddd4j.sample.order.domain.model.TradeOrderItem;
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

/** \u9a8c\u8bc1\u5546\u54c1\u4ea4\u6613\u793a\u4f8b\u7684\u5c42\u95f4\u5236\u54c1\u8fb9\u754c\u3001\u805a\u5408\u7ea6\u675f\u548c\u6295\u5f71\u5e76\u53d1\u987a\u5e8f\u3002 */
class CatalogArchitectureTest {
    /** \u9a8c\u8bc1 HTTP \u9002\u914d\u5c42\u548c JDBC \u6301\u4e45\u5316\u5c42\u6765\u81ea\u4e0d\u540c\u6784\u5efa\u5236\u54c1\u3002 */
    @Test
    void jdbcAndHttpAreDistinctArtifacts() {
        assertNotEquals(CatalogApi.class.getProtectionDomain().getCodeSource().getLocation(),
                CatalogDatabase.class.getProtectionDomain().getCodeSource().getLocation());
    }

    /** \u9a8c\u8bc1\u5546\u54c1\u6216\u8ba2\u5355\u805a\u5408\u62d2\u7edd\u5c5e\u4e8e\u5176\u4ed6\u79df\u6237\u7684\u5b50\u5bf9\u8c61\u3002 */
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

    /** \u9a8c\u8bc1\u9886\u57df\u805a\u5408\u672a\u516c\u5f00\u7ed5\u8fc7\u6821\u9a8c\u5165\u53e3\u7684\u6784\u5efa\u65b9\u6cd5\u3002 */
    @Test
    void aggregateCannotBeBuiltWithoutItsValidatedEntryPoints() {
        assertThrows(NoSuchMethodException.class, () -> TradeOrder.class.getMethod("builder"));
        assertThrows(NoSuchMethodException.class, () -> GoodsSpu.class.getMethod("builder"));
        var invalidLine = TradeOrderItem.builder().tenantId("tenant").quantity(-1L).unitPriceCent(1200L).build();
        assertThrows(IllegalArgumentException.class,
                () -> TradeOrder.place("tenant", "O-1", 3L, List.of(invalidLine)));
    }

    /** \u9a8c\u8bc1\u8fd8\u539f\u7684\u8ba2\u5355\u72ec\u7acb\u6301\u6709\u660e\u7ec6\uff0c\u8c03\u7528\u65b9\u4fee\u6539\u539f\u59cb\u96c6\u5408\u65e0\u6cd5\u6539\u53d8\u805a\u5408\u3002 */
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
     * \u9a8c\u8bc1\u540c\u4e00\u8ba2\u5355\u7684\u7b2c\u4e8c\u6b21\u5e76\u53d1\u6295\u5f71\u9700\u7b49\u7b2c\u4e00\u6b21\u53d1\u5e03\u540e\u65b9\u53ef\u8bfb\u53d6\u3002
     * @throws Exception \u6d4b\u8bd5\u7ebf\u7a0b\u83b7\u53d6\u7ed3\u679c\u5931\u8d25\u6216\u7b49\u5f85\u8d85\u65f6
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

    /**
     * \u6784\u9020\u67b6\u6784\u6d4b\u8bd5\u6240\u7528\u7684\u5355\u884c\u8ba2\u5355\u660e\u7ec6\u3002
     * @return \u6570\u91cf\u4e3a\u4e00\u7684\u793a\u4f8b\u8ba2\u5355\u660e\u7ec6
     */
    private static TradeOrderItem line() {
        return TradeOrderItem.fromOffer("tenant", 1, new GoodsSkuOffer(5L, "Book", "Edition", 1200L), 1L);
    }

    /**
     * \u7b49\u5f85\u5e76\u53d1\u6d4b\u8bd5\u4e2d\u7684\u6307\u5b9a\u9636\u6bb5\uff0c\u5728\u8d85\u65f6\u6216\u4e2d\u65ad\u65f6\u4f7f\u65ad\u8a00\u5931\u8d25\u3002
     * @param latch \u7528\u6765\u540c\u6b65\u4e24\u4e2a\u5e76\u53d1\u6d4b\u8bd5\u9636\u6bb5\u7684\u95e8\u95e9
     * @throws AssertionError \u7b49\u5f85\u95e8\u95e9\u8d85\u65f6\u6216\u5f53\u524d\u7ebf\u7a0b\u88ab\u4e2d\u65ad
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
     * 验证真实商品交易类满足 COLA 依赖规则。
     */
    @Test
    void catalogProductionClassesRespectColaBoundaries() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("io.ddd4j.sample.order");
        assertTrue(classes.contain(TradeOrder.class));
        assertTrue(classes.contain(CatalogApi.class));
        assertTrue(classes.contain(CatalogDatabase.class));
        assertTrue(classes.contain(CatalogService.class));
        ColaDDDLayerRules.DOMAIN_NOT_DEPEND_ON_ADAPTER.check(classes);
        ColaDDDLayerRules.DOMAIN_NOT_DEPEND_ON_APPLICATION.check(classes);
        ColaDDDLayerRules.DOMAIN_NOT_DEPEND_ON_FRAMEWORK.check(classes);
    }

    /**
     * 验证真实的 app 包名同样会触发领域反向依赖违规。
     */
    @Test
    void domainDependingOnAppIsRejected() {
        var classes = new ClassFileImporter()
                .importClasses(InvalidDomainDependency.class);
        var result = ColaDDDLayerRules.DOMAIN_NOT_DEPEND_ON_APPLICATION.evaluate(classes);
        assertTrue(result.hasViolation());
    }
}
