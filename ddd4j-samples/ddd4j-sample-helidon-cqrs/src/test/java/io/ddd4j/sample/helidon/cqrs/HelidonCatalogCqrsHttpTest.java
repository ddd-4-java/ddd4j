package io.ddd4j.sample.helidon.cqrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.helidon.microprofile.server.Server;
import io.ddd4j.sample.order.domain.event.TradeOrderPaymentConfirmed;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.spi.Extension;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import io.ddd4j.sample.order.testkit.http.HttpClient;
import io.ddd4j.sample.order.testkit.http.HttpRequest;
import io.ddd4j.sample.order.testkit.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
class HelidonCatalogCqrsHttpTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    private final String tenantId = UUID.randomUUID().toString().substring(0, 20);
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void nativeServerRebuildsPaymentSnapshotAfterRestart() throws Exception {
        Server server = start();
        try {
            request(server, "POST", "/goods/LUNCH", 201, "{\"spuName\":\"Lunch\",\"skuCode\":\"S1\",\"skuName\":\"Weekday\",\"salePriceCent\":1250,\"shopId\":7}\n");
            request(server, "POST", "/orders/T001", 201, "{\"shopId\":7,\"items\":[{\"skuCode\":\"S1\",\"quantity\":2}]}\n");
            request(server, "GET", "/orders/T001", 404, "");
            request(server, "POST", "/orders/T001/payment", 200, "{\"paidAmountCent\":2500}");
            request(server, "POST", "/orders/T001/payment", 200, "{\"paidAmountCent\":2500}");
            assertEquals(1, TradeEventObserver.PAID.size());
            assertEquals(tenantId, TradeEventObserver.PAID.element().getTenantId());
            verifyProjection(server);
        } finally {
            server.stop();
        }
        Server reopened = start();
        try {
            request(reopened, "GET", "/orders/T001", 404, "");
            verifyProjection(reopened);
        } finally {
            reopened.stop();
        }
    }

    public static final class TradeEventObserverExtension implements Extension {
        public void install(@Observes BeforeBeanDiscovery discovery, BeanManager manager) {
            discovery.addAnnotatedType(manager.createAnnotatedType(TradeEventObserver.class), "catalog-trade-event-test");
        }
    }

    @ApplicationScoped
    public static class TradeEventObserver {
        static final ConcurrentLinkedQueue<TradeOrderPaymentConfirmed> PAID = new ConcurrentLinkedQueue<>();

        public void onPayment(@Observes TradeOrderPaymentConfirmed payment) {
            PAID.add(payment);
        }
    }

    private void verifyProjection(Server server) throws Exception {
        request(server, "POST", "/projections/orders/T001", 200, "");
        com.fasterxml.jackson.databind.JsonNode order = json.readTree(request(server, "GET", "/orders/T001", 200, ""));
        assertEquals("CONFIRMED", order.path("orderState").asText());
        assertEquals(2500, order.path("paidAmountCent").asLong());
        assertEquals(1250, order.at("/items/0/unitPriceCent").asLong());
    }

    private Server start() {
        UnpooledDataSource source = new UnpooledDataSource(MYSQL.getDriverClassName(), MYSQL.getJdbcUrl(),
                MYSQL.getUsername(), MYSQL.getPassword());
        return HelidonCqrsApplication.start(0, CatalogRuntime.start(source));
    }

    private String request(Server server, String method, String path,
                           int status, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/orders/catalog" + path))
                .header("X-Tenant-Id", tenantId).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<java.lang.String> response = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), response.body());
        return response.body();
    }
}
