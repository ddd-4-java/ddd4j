package io.ddd4j.sample.order;

import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import io.ddd4j.sample.order.app.projection.TradeOrderSnapshot;
import java.util.Collections;
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

class CatalogArchitectureTest {
    @Test
    void jdbcAndHttpAreDistinctArtifacts() {
        assertNotEquals(CatalogApi.class.getProtectionDomain().getCodeSource().getLocation(),
                CatalogDatabase.class.getProtectionDomain().getCodeSource().getLocation());
    }

    @Test
    void aggregatesRejectChildrenFromAnotherTenant() {
        GoodsSkuOffer offer = new GoodsSkuOffer(5L, "Book", "Edition", 1200L);
        TradeOrderItem line = TradeOrderItem.fromOffer("other-tenant", 1, offer, 1L);
        assertThrows(IllegalArgumentException.class,
                () -> TradeOrder.place("tenant", "O-1", 3L, Collections.singletonList(line)));
        GoodsSku sku = GoodsSku.unlimited("other-tenant", "S-1", "Edition", 1200L);
        assertThrows(IllegalArgumentException.class,
                () -> GoodsSpu.listPackage("tenant", "G-1", "Book", 3L, sku));
    }

    @Test
    void aggregateCannotBeBuiltWithoutItsValidatedEntryPoints() {
        assertThrows(NoSuchMethodException.class, () -> TradeOrder.class.getMethod("builder"));
        assertThrows(NoSuchMethodException.class, () -> GoodsSpu.class.getMethod("builder"));
        TradeOrderItem invalidLine = TradeOrderItem.builder().tenantId("tenant").quantity(-1L).unitPriceCent(1200L).build();
        assertThrows(IllegalArgumentException.class,
                () -> TradeOrder.place("tenant", "O-1", 3L, Collections.singletonList(invalidLine)));
    }

    @Test
    void restoredAggregateOwnsItsItemCollection() {
        List<TradeOrderItem> items = new ArrayList<>(Collections.singletonList(line()));
        TradeOrder order = TradeOrder.restore(1L, "tenant", "O-1", "GROUP_BUY", "VOUCHER", 3L, null,
                "AWAITING_PAYMENT", "PENDING", "NONE", null, null, 1200L, null,
                null, 0L, null, 0L, null, 0, items);
        items.clear();
        assertEquals(1, order.getItems().size());
        assertThrows(UnsupportedOperationException.class, () -> order.getItems().clear());
    }

    @Test
    void concurrentRebuildCannotReadTheSameKeyBeforeOlderRebuildPublishes() throws Exception {
        TradeOrder pending = TradeOrder.place("tenant", "O-1", 3L, Collections.singletonList(line()));
        TradeOrder confirmed = pending.confirmPayment(1200L, LocalDateTime.of(2026, 1, 1, 0, 0));
        AtomicReference<TradeOrder> persisted = new AtomicReference<>(pending);
        AtomicInteger reads = new AtomicInteger();
        CountDownLatch oldRead = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch newRead = new CountDownLatch(1);
        CountDownLatch secondRequest = new CountDownLatch(1);
        CatalogOrderView view = new CatalogOrderView(query -> {
            TradeOrder snapshot = persisted.get();
            if (reads.incrementAndGet() == 1) {
                oldRead.countDown();
                await(release);
            } else {
                newRead.countDown();
            }
            return snapshot;
        });

        TradeOrderQuery query = new TradeOrderQuery("tenant", "O-1");
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<TradeOrderSnapshot> older = workers.submit(() -> view.rebuild(query));
            await(oldRead);
            persisted.set(confirmed);
            Future<TradeOrderSnapshot> newer = workers.submit(() -> {
                secondRequest.countDown();
                return view.rebuild(query);
            });
            await(secondRequest);
            assertFalse(newRead.await(200, TimeUnit.MILLISECONDS));
            release.countDown();
            assertEquals("AWAITING_PAYMENT", older.get(5, TimeUnit.SECONDS).getOrderState());
            assertEquals("CONFIRMED", newer.get(5, TimeUnit.SECONDS).getOrderState());
            TradeOrderSnapshot result = view.handle(new ProjectedTradeOrderQuery("tenant", "O-1"));
            assertEquals("CONFIRMED", result.getOrderState());
            assertEquals(1200, result.getItems().get(0).getUnitPriceCent());
            assertThrows(UnsupportedOperationException.class, () -> result.getItems().clear());
        } finally {
            release.countDown();
            workers.shutdownNow();
        }
    }

    private static TradeOrderItem line() {
        return TradeOrderItem.fromOffer("tenant", 1, new GoodsSkuOffer(5L, "Book", "Edition", 1200L), 1L);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }
}
