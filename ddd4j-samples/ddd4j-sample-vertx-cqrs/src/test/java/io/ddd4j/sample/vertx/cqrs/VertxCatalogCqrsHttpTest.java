package io.ddd4j.sample.vertx.cqrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.order.domain.event.TradeOrderPaymentConfirmed;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.vertx.VertxDomainEventPublisher;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Testcontainers
class VertxCatalogCqrsHttpTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    private final String tenantId = UUID.randomUUID().toString().substring(0, 20);
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void nativeBusPublishesCommittedPaymentAndProjectionRebuildsAfterRestart() throws Exception {
        try (var server = start()) {
            var events = new LinkedBlockingQueue<TradeOrderPaymentConfirmed>();
            var listener = server.vertx().eventBus().<Object>consumer(
                    VertxDomainEventPublisher.ADDRESS, msg -> {
                        if (msg.body() instanceof TradeOrderPaymentConfirmed payment) {
                            events.add(payment);
                        }
                    });
            var ready = new java.util.concurrent.CompletableFuture<Void>();
            listener.completionHandler(result -> {
                if (result.succeeded()) {
                    ready.complete(null);
                } else {
                    ready.completeExceptionally(result.cause());
                }
            });
            ready.get(5, TimeUnit.SECONDS);
            request(server, "POST", "/goods/LUNCH", 201, """
                    {"spuName":"Lunch","skuCode":"S1","skuName":"Weekday","salePriceCent":1250,"shopId":7}
                    """);
            request(server, "POST", "/orders/T001", 201, """
                    {"shopId":7,"items":[{"skuCode":"S1","quantity":2}]}
                    """);
            request(server, "GET", "/orders/T001", 404, "");
            request(server, "POST", "/orders/T001/payment", 200, "{\"paidAmountCent\":2500}");
            request(server, "POST", "/orders/T001/payment", 200, "{\"paidAmountCent\":2500}");
            var event = events.poll(5, TimeUnit.SECONDS);
            assertNotNull(event);
            assertEquals(tenantId, event.getTenantId());
            assertEquals(2500, event.getPaidAmountCent());
            assertNull(events.poll(250, TimeUnit.MILLISECONDS));
            verifyProjection(server);
        }
        try (var reopened = start()) {
            request(reopened, "GET", "/orders/T001", 404, "");
            verifyProjection(reopened);
        }
    }

    private void verifyProjection(VertxCqrsApplication.CatalogServer server) throws Exception {
        request(server, "POST", "/projections/orders/T001", 200, "");
        var order = json.readTree(request(server, "GET", "/orders/T001", 200, ""));
        assertEquals("CONFIRMED", order.path("orderState").asText());
        assertEquals(2500, order.path("paidAmountCent").asLong());
        assertEquals(1250, order.at("/items/0/unitPriceCent").asLong());
    }

    private VertxCqrsApplication.CatalogServer start() {
        var source = new UnpooledDataSource(MYSQL.getDriverClassName(), MYSQL.getJdbcUrl(),
                MYSQL.getUsername(), MYSQL.getPassword());
        return VertxCqrsApplication.startCatalog(0, publisher -> CatalogRuntime.start(source, publisher));
    }

    private String request(VertxCqrsApplication.CatalogServer server, String method, String path,
                           int status, String body) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/orders/catalog" + path))
                .header("X-Tenant-Id", tenantId).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        var response = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), response.body());
        return response.body();
    }
}
