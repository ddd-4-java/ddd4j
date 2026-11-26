package io.ddd4j.sample.spring.cqrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.spring.command.SpringCommandBus;
import io.ddd4j.sample.order.domain.event.TradeOrderPaymentConfirmed;
import org.springframework.context.PayloadApplicationEvent;
import java.util.concurrent.CopyOnWriteArrayList;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Testcontainers
class SpringCatalogCqrsHttpTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final String tenant = UUID.randomUUID().toString().substring(0, 20);

    @Test
    void nativeBusCommitsPaymentAndReopenedViewRebuildsFromFourTables() throws Exception {
        try (var app = start()) {
            var events = new CopyOnWriteArrayList<TradeOrderPaymentConfirmed>();
            app.addApplicationListener(event -> {
                if (event instanceof PayloadApplicationEvent<?> payload
                        && payload.getPayload() instanceof TradeOrderPaymentConfirmed confirmed) {
                    events.add(confirmed);
                }
            });
            assertEquals(1, app.getBeansOfType(CommandBus.class).size());
            assertInstanceOf(SpringCommandBus.class, app.getBean(CommandBus.class));
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
            assertEquals(1, events.size());
            assertEquals(2500, events.get(0).getPaidAmountCent());
            verifyProjection(app);
        }
        try (var reopened = start()) {
            expect(reopened, "GET", "/orders/catalog/orders/T001", null, 404);
            verifyProjection(reopened);
        }
    }

    private void verifyProjection(ServletWebServerApplicationContext app) throws Exception {
        expect(app, "POST", "/orders/catalog/projections/orders/T001", null, 200);
        var order = json.readTree(expect(app, "GET", "/orders/catalog/orders/T001", null, 200));
        assertEquals("CONFIRMED", order.path("orderState").asText());
        assertEquals(2500, order.path("paidAmountCent").asLong());
        assertEquals(1250, order.at("/items/0/unitPriceCent").asLong());
    }

    private ServletWebServerApplicationContext start() {
        return (ServletWebServerApplicationContext) SpringApplication.run(SpringCqrsSampleApplication.class,
                "--spring.profiles.active=catalog-trade", "--server.port=0", "--CATALOG_JDBC_URL=" + MYSQL.getJdbcUrl(),
                "--CATALOG_DB_USER=" + MYSQL.getUsername(), "--CATALOG_DB_PASSWORD=" + MYSQL.getPassword());
    }

    private String expect(ServletWebServerApplicationContext app, String method, String path,
                          String body, int status) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.getWebServer().getPort() + path))
                .header("X-Tenant-Id", tenant)
                .header("Content-Type", "application/json")
                .method(method, java.util.Objects.isNull(body) ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body)).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), response.body());
        return response.body();
    }
}
