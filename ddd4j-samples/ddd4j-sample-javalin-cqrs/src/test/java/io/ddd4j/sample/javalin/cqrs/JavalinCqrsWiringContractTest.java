package io.ddd4j.sample.javalin.cqrs;

import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.ddd4j.guice.context.GuiceContext;
import io.ddd4j.sample.javalin.cqrs.shared.SharedCqrsRuntime;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.testkit.EnvelopeOrderHttpDriver;
import io.ddd4j.sample.order.testkit.HttpOrderSampleDriver;
import io.ddd4j.sample.order.testkit.SampleHttpTransport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavalinCqrsWiringContractTest {

    @Test
    void defaultModeKeepsAnonymousLegacyOrderRoutesAndOmitsDurableRoutes() throws Exception {
        try (JavalinCqrsApplication.Application app = JavalinCqrsApplication.start(0, null)) {
            String base = base(app);
            assertEquals(404, get(base, "/sample/orders/missing", Collections.emptyMap()).statusCode());
            assertEquals(404, SampleHttpTransport.request(base + "/sample/projections/orders/run",
                    "POST", Collections.emptyMap(), Collections.emptyMap(), null).statusCode());
            String orderId = assertLegacyOrderContract(base, "wiring-default");
            assertEquals(200, get(base, "/api/orders/query/detail/" + orderId,
                    Collections.singletonMap("Authorization", "Bearer ignored-in-plain-sample")).statusCode());
        }
    }

    @Test
    void scheduledDurableModeProjectsWithoutManualTriggerAndKeepsLegacyStoreSeparate() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withTmpFs(Collections.singletonMap("/var/lib/postgresql/data", "rw,size=512m"))) {
            postgres.start();
            PGSimpleDataSource source = new PGSimpleDataSource();
            source.setUrl(postgres.getJdbcUrl());
            source.setUser(postgres.getUsername());
            source.setPassword(postgres.getPassword());
            Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();

            SharedCqrsRuntime runtime = new SharedCqrsRuntime(source, "create", true, UnaryOperator.identity());
            long position;
            String sharedId;
            try (JavalinCqrsApplication.Application app = JavalinCqrsApplication.start(0, runtime)) {
                String base = base(app);
                assertPositionRepositoryContract(GuiceContext.getInjector().getInstance(ProjectionPositionRepository.class));
                SampleHttpTransport.Response missing = get(base, "/sample/orders/missing", Collections.emptyMap());
                assertEquals(500, missing.statusCode(), missing.body());
                assertTrue(missing.body().contains("\"msg\":\"No value present\""), missing.body());

                HttpOrderSampleDriver shared = new HttpOrderSampleDriver(base + "/sample");
                sharedId = shared.create(new CreateOrderCommand("wiring-shared", "buyer-1", "Alice"));
                shared.addLine(new AddOrderLineCommand(sharedId, "goods-1", "DDD Book", 2, new BigDecimal("12.50")));
                shared.pay(sharedId, "wiring-shared-payment");

                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(22);
                Optional<OrderReadModel> persisted = runtime.queries().findProjectionById(sharedId);
                position = runtime.projection().position();
                while ((!isPaid(persisted) || position == 0) && System.nanoTime() < deadline) {
                    Thread.sleep(100);
                    persisted = runtime.queries().findProjectionById(sharedId);
                    position = runtime.projection().position();
                }
                assertTrue(isPaid(persisted), "the scheduled Guice projection did not commit a paid order");
                assertTrue(position > 0, "the scheduled Guice projection did not persist its checkpoint");
                assertEquals(0, new BigDecimal("25.00").compareTo(shared.find(sharedId).totalAmount()));
                assertEquals(3, runtime.projection().events(sharedId).size());

                String legacyId = assertLegacyOrderContract(base, "wiring-legacy-during-durable");
                SampleHttpTransport.Response legacyMissing = get(base, "/api/orders/query/detail/" + sharedId,
                        Collections.emptyMap());
                assertEquals(200, legacyMissing.statusCode(), legacyMissing.body());
                assertTrue(legacyMissing.body().contains("\"code\":\"404\""), legacyMissing.body());
                assertFalse(runtime.queries().findProjectionById(legacyId).isPresent());
                assertTrue(runtime.projection().events(legacyId).isEmpty());
                System.out.printf("R14_JAVALIN_SCHEDULED_WIRING status=PAID position=%s events=3%n", position);
            }
            assertFalse(runtime.factory().isOpen());

            SharedCqrsRuntime reopened = new SharedCqrsRuntime(source, "validate", false, UnaryOperator.identity());
            try (JavalinCqrsApplication.Application app = JavalinCqrsApplication.start(0, reopened)) {
                assertEquals(position, reopened.projection().position());
                assertEquals(0, new BigDecimal("25.00").compareTo(
                        new HttpOrderSampleDriver(base(app) + "/sample").find(sharedId).totalAmount()));
            }
            assertFalse(reopened.factory().isOpen());
        }
    }

    private static boolean isPaid(Optional<OrderReadModel> order) {
        return order.isPresent() && "PAID".equals(order.get().status().toString());
    }

    private static void assertPositionRepositoryContract(ProjectionPositionRepository positions) {
        String key = "r14-javalin-independent-position";
        assertFalse(positions.findByStreamId(key).isPresent());
        assertEquals(11L, positions.save(new DefaultProjectionPosition(key, 11L)).getNextEventNumber());
        assertEquals(11L, positions.findByStreamId(key).get().getNextEventNumber());
        assertTrue(positions.findAll().stream().anyMatch(p -> key.equals(p.getStreamId())
                && p.getNextEventNumber() == 11L));
        positions.resetToZero(key);
        assertEquals(0L, positions.findByStreamId(key).get().getNextEventNumber());
        positions.deleteByStreamId(key);
        assertFalse(positions.findByStreamId(key).isPresent());
    }

    private static String assertLegacyOrderContract(String base, String number) {
        EnvelopeOrderHttpDriver legacy = new EnvelopeOrderHttpDriver(base, "/api/orders", Collections.emptyMap());
        String id = legacy.create(new CreateOrderCommand(number, "buyer-1", "Alice"));
        assertFalse(id.isEmpty());
        legacy.addLine(new AddOrderLineCommand(id, "goods-1", "DDD Book", 2, new BigDecimal("12.50")));
        legacy.pay(id, number + "-payment");
        assertEquals(1, legacy.request("GET", "/api/orders/query/detail/" + id, null,
                Collections.emptyMap()).path("lineCount").asInt());
        assertEquals(0, new BigDecimal("25.00").compareTo(legacy.request("GET",
                "/api/orders/query/detail/" + id, null, Collections.emptyMap())
                .path("totalAmount").path("amount").decimalValue()));
        return id;
    }

    private static String base(JavalinCqrsApplication.Application app) {
        return "http://127.0.0.1:" + app.app().port();
    }

    private static SampleHttpTransport.Response get(String base, String path, Map<String, String> headers)
            throws Exception {
        return SampleHttpTransport.request(base + path, "GET", headers, Collections.emptyMap(), null);
    }
}
