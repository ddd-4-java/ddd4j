package io.ddd4j.sample.micronaut.cqrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.micronaut.command.MicronautCommandBus;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.context.event.ApplicationEventListener;
import io.ddd4j.sample.order.domain.event.TradeOrderPaymentConfirmed;
import jakarta.inject.Singleton;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Map;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Testcontainers
class MicronautCatalogCqrsHttpTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final String tenant = UUID.randomUUID().toString().substring(0, 20);

    @Test
    void nativeBusCommitsPaymentAndReopenedViewRebuildsFromFourTables() throws Exception {
        try (var app = start()) {
            assertEquals(1, app.getApplicationContext().getBeansOfType(CommandBus.class).size());
            assertInstanceOf(MicronautCommandBus.class, app.getApplicationContext().getBean(CommandBus.class));
            expect(app, "POST", "/orders", "{}", 404);
            expect(app, "POST", "/orders/catalog/goods/LUNCH", """
                    {"spuName":"Lunch","skuCode":"S1","skuName":"Weekday","salePriceCent":1250,"shopId":7}
                    """, 201);
            expect(app, "POST", "/orders/catalog/orders/T001", """
                    {"shopId":7,"items":[{"skuCode":"S1","quantity":2}]}
                    """, 201);
            expect(app, "GET", "/orders/catalog/orders/T001", null, 404);
            expect(app, "POST", "/orders/catalog/orders/T001/payment", "{\"paidAmountCent\":2499}", 409);
            expect(app, "POST", "/orders/catalog/orders/T001/payment", "{\"paidAmountCent\":2500}", 200);
            expect(app, "POST", "/orders/catalog/orders/T001/payment", "{\"paidAmountCent\":2500}", 200);
            var paid = app.getApplicationContext().getBean(PaidEvents.class).events;
            assertEquals(1, paid.size());
            assertEquals(tenant, paid.element().getTenantId());
            verifyProjection(app);
        }
        try (var reopened = start()) {
            expect(reopened, "GET", "/orders/catalog/orders/T001", null, 404);
            verifyProjection(reopened);
        }
    }

    @Singleton
    public static class PaidEvents implements ApplicationEventListener<TradeOrderPaymentConfirmed> {
        final ConcurrentLinkedQueue<TradeOrderPaymentConfirmed> events = new ConcurrentLinkedQueue<>();

        @Override
        public void onApplicationEvent(TradeOrderPaymentConfirmed event) {
            events.add(event);
        }
    }

    private void verifyProjection(EmbeddedServer app) throws Exception {
        expect(app, "POST", "/orders/catalog/projections/orders/T001", null, 200);
        var order = json.readTree(expect(app, "GET", "/orders/catalog/orders/T001", null, 200));
        assertEquals("CONFIRMED", order.path("orderState").asText());
        assertEquals(2500, order.path("paidAmountCent").asLong());
        assertEquals(1250, order.at("/items/0/unitPriceCent").asLong());
    }

    private EmbeddedServer start() {
        return ApplicationContext.run(EmbeddedServer.class, Map.of("micronaut.server.port", -1,
                "catalog.jdbc.url", MYSQL.getJdbcUrl(), "catalog.db.user", MYSQL.getUsername(),
                "catalog.db.password", MYSQL.getPassword()));
    }

    private String expect(EmbeddedServer app, String method, String path,
                          String body, int status) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.getPort() + path))
                .header("X-Tenant-Id", tenant)
                .header("Content-Type", "application/json")
                .method(method, java.util.Objects.isNull(body) ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body)).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), response.body());
        return response.body();
    }
}
