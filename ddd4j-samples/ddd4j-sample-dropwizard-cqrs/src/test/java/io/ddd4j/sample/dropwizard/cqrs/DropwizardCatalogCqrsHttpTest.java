package io.ddd4j.sample.dropwizard.cqrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.order.domain.event.TradeOrderPaymentConfirmed;
import io.dropwizard.Application;
import io.dropwizard.server.DefaultServerFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.testing.DropwizardTestSupport;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import io.ddd4j.sample.order.testkit.http.HttpClient;
import io.ddd4j.sample.order.testkit.http.HttpRequest;
import io.ddd4j.sample.order.testkit.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
class DropwizardCatalogCqrsHttpTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final String tenant = UUID.randomUUID().toString().substring(0, 20);

    private final ConcurrentLinkedQueue<TradeOrderPaymentConfirmed> paid = new ConcurrentLinkedQueue<>();

    @Test
    void nativeServerRebuildsPaidOrderFromMySqlOnRestart() throws Exception {
        DropwizardTestSupport<io.ddd4j.sample.dropwizard.cqrs.DropwizardCqrsConfiguration> app = application();
        try {
            app.before();
            expect(app, "POST", "/goods/LUNCH", 201, "{\"spuName\":\"Lunch\",\"skuCode\":\"S1\",\"skuName\":\"Weekday\",\"salePriceCent\":1250,\"shopId\":7}\n");
            expect(app, "POST", "/orders/T001", 201, "{\"shopId\":7,\"items\":[{\"skuCode\":\"S1\",\"quantity\":2}]}\n");
            expect(app, "GET", "/orders/T001", 404, "");
            expect(app, "POST", "/orders/T001/payment", 200, "{\"paidAmountCent\":2500}");
            expect(app, "POST", "/orders/T001/payment", 200, "{\"paidAmountCent\":2500}");
            assertEquals(1, paid.size());
            assertEquals(tenant, paid.element().getTenantId());
            projectAndCheck(app);
        } finally {
            app.after();
        }
        DropwizardTestSupport<io.ddd4j.sample.dropwizard.cqrs.DropwizardCqrsConfiguration> reopened = application();
        try {
            reopened.before();
            expect(reopened, "GET", "/orders/T001", 404, "");
            projectAndCheck(reopened);
        } finally {
            reopened.after();
        }
    }

    private void projectAndCheck(DropwizardTestSupport<DropwizardCqrsConfiguration> app) throws Exception {
        expect(app, "POST", "/projections/orders/T001", 200, "");
        com.fasterxml.jackson.databind.JsonNode order = json.readTree(expect(app, "GET", "/orders/T001", 200, ""));
        assertEquals("CONFIRMED", order.path("orderState").asText());
        assertEquals(2500, order.path("paidAmountCent").asLong());
    }

    private DropwizardTestSupport<DropwizardCqrsConfiguration> application() {
        io.ddd4j.sample.dropwizard.cqrs.DropwizardCqrsConfiguration config = new DropwizardCqrsConfiguration();
        config.setCatalogJdbcUrl(MYSQL.getJdbcUrl());
        config.setCatalogDbUser(MYSQL.getUsername());
        config.setCatalogDbPassword(MYSQL.getPassword());
        DefaultServerFactory server = (DefaultServerFactory) config.getServerFactory();
        ((HttpConnectorFactory) server.getApplicationConnectors().get(0)).setPort(0);
        ((HttpConnectorFactory) server.getAdminConnectors().get(0)).setPort(0);
        return new DropwizardTestSupport<DropwizardCqrsConfiguration>(DropwizardCqrsApplication.class, config) {
            @Override
            public Application<DropwizardCqrsConfiguration> newApplication() {
                return new DropwizardCqrsApplication(event -> {
                    if (event instanceof TradeOrderPaymentConfirmed) {
                        TradeOrderPaymentConfirmed payment = (TradeOrderPaymentConfirmed) event;
                        paid.add(payment);
                    }
                });
            }
        };
    }

    private String expect(DropwizardTestSupport<DropwizardCqrsConfiguration> app, String method,
                          String path, int status, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.getLocalPort() + "/orders/catalog" + path))
                .header("X-Tenant-Id", tenant).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<java.lang.String> response = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), response.body());
        return response.body();
    }
}
