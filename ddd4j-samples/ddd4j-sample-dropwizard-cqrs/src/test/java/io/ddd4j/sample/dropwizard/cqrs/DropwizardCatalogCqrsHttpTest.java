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

/** \u901a\u8fc7 <code>application()</code>\u3001<code>app.before()</code>\u3001<code>expect()</code> \u9a8c\u8bc1 <code>DropwizardCatalogCqrsHttp</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
@Testcontainers
class DropwizardCatalogCqrsHttpTest {
    /** \u4e3a\u5f53\u524d\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u72ec\u7acb MySQL \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    /** \u53d1\u9001\u5408\u540c\u6d4b\u8bd5\u8bf7\u6c42\u7684 HTTP \u5ba2\u6237\u7aef\u3002 */
    private final HttpClient http = HttpClient.newHttpClient();
    /** \u8d1f\u8d23\u8f6c\u6362\u6d4b\u8bd5\u6216\u534f\u8bae\u6b63\u6587\u4e2d JSON \u6570\u636e\u7684 Jackson \u6620\u5c04\u5668\u3002 */
    private final ObjectMapper json = new ObjectMapper();
    /** \u4e1a\u52a1\u6570\u636e\u6216\u8ba4\u8bc1\u8bf7\u6c42\u6240\u5c5e\u7684\u6d4b\u8bd5\u79df\u6237\u3002 */
    private final String tenant = UUID.randomUUID().toString().substring(0, 20);

    /** \u8bb0\u5f55\u6837\u4f8b\u8ba2\u5355\u662f\u5426\u5df2\u7ecf\u786e\u8ba4\u4ed8\u6b3e\u3002 */
    private final ConcurrentLinkedQueue<TradeOrderPaymentConfirmed> paid = new ConcurrentLinkedQueue<>();

    /**
     * \u9a8c\u8bc1<code>paid.size()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>paid.element().getTenantId()</code> \u7b49\u4e8e <code>tenant</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
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

    /**
     * \u8c03\u7528 <code>expect()</code>\u3001<code>json.readTree()</code>\u3001<code>order.path()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param app \u4f20\u7ed9 <code>expect()</code> \u7684 <code>DropwizardTestSupport&lt;DropwizardCqrsConfiguration&gt;</code> \u8f93\u5165
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    private void projectAndCheck(DropwizardTestSupport<DropwizardCqrsConfiguration> app) throws Exception {
        expect(app, "POST", "/projections/orders/T001", 200, "");
        com.fasterxml.jackson.databind.JsonNode order = json.readTree(expect(app, "GET", "/orders/T001", 200, ""));
        assertEquals("CONFIRMED", order.path("orderState").asText());
        assertEquals(2500, order.path("paidAmountCent").asLong());
    }

    /**
     * \u8c03\u7528 <code>config.setCatalogJdbcUrl()</code>\u3001<code>MYSQL.getJdbcUrl()</code>\u3001<code>config.setCatalogDbUser()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>DropwizardTestSupport&lt;DropwizardCqrsConfiguration&gt;</code> \u7ed3\u679c
     */
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

    /**
     * \u901a\u8fc7 <code>response.body()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param app \u4f20\u7ed9 <code>HttpRequest.newBuilder()</code> \u7684 <code>DropwizardTestSupport&lt;DropwizardCqrsConfiguration&gt;</code> \u8f93\u5165
     * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param status \u5f53\u524d HTTP \u6216\u4e1a\u52a1\u6d41\u7a0b\u7684\u72b6\u6001
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>response.body()</code>
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    private String expect(DropwizardTestSupport<DropwizardCqrsConfiguration> app, String method,
                          String path, int status, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.getLocalPort() + "/orders/catalog" + path))
                .header("X-Tenant-Id", tenant).header("Idempotency-Key", path.endsWith("/payment") ? "checkout-confirmation" : "").header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<java.lang.String> response = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), response.body());
        return response.body();
    }
}
