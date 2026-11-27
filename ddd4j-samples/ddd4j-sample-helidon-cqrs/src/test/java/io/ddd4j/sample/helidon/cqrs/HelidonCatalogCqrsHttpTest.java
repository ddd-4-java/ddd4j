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

/** \u901a\u8fc7 <code>start()</code>\u3001<code>request()</code>\u3001<code>TradeEventObserver.PAID.size()</code> \u9a8c\u8bc1 <code>HelidonCatalogCqrsHttp</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
@Testcontainers
class HelidonCatalogCqrsHttpTest {
    /** \u4e3a\u5f53\u524d\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u72ec\u7acb MySQL \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    /** \u5f53\u524d\u4e1a\u52a1\u6570\u636e\u6240\u5c5e\u7684\u79df\u6237\u6807\u8bc6\u3002 */
    private final String tenantId = UUID.randomUUID().toString().substring(0, 20);
    /** \u53d1\u9001\u5408\u540c\u6d4b\u8bd5\u8bf7\u6c42\u7684 HTTP \u5ba2\u6237\u7aef\u3002 */
    private final HttpClient http = HttpClient.newHttpClient();
    /** \u8d1f\u8d23\u8f6c\u6362\u6d4b\u8bd5\u6216\u534f\u8bae\u6b63\u6587\u4e2d JSON \u6570\u636e\u7684 Jackson \u6620\u5c04\u5668\u3002 */
    private final ObjectMapper json = new ObjectMapper();

    /**
     * \u9a8c\u8bc1<code>TradeEventObserver.PAID.size()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>TradeEventObserver.PAID.element().getTenantId()</code> \u7b49\u4e8e <code>tenantId</code>\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
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

    /** \u5b9e\u73b0 <code>Extension</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>install()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    public static final class TradeEventObserverExtension implements Extension {
        /**
         * \u8c03\u7528 <code>discovery.addAnnotatedType()</code>\u3001<code>manager.createAnnotatedType()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param discovery \u4f20\u7ed9 <code>discovery.addAnnotatedType()</code> \u7684 <code>BeforeBeanDiscovery</code> \u8f93\u5165
         * @param manager \u4f20\u7ed9 <code>discovery.addAnnotatedType()</code> \u7684 <code>BeanManager</code> \u8f93\u5165
         */
        public void install(@Observes BeforeBeanDiscovery discovery, BeanManager manager) {
            discovery.addAnnotatedType(manager.createAnnotatedType(TradeEventObserver.class), "catalog-trade-event-test");
        }
    }

    /** \u5411\u6d4b\u8bd5\u5bb9\u5668\u6ce8\u518c <code>onPayment()</code> \u7b49\u6d4b\u8bd5\u5165\u53e3\u6216\u7ec4\u4ef6\u3002 */
    @ApplicationScoped
    public static class TradeEventObserver {
        /** \u8868\u793a\u6d4b\u8bd5\u8ba2\u5355\u5df2\u7ecf\u5b8c\u6210\u4ed8\u6b3e\u7684\u72b6\u6001\u503c\u3002 */
        static final ConcurrentLinkedQueue<TradeOrderPaymentConfirmed> PAID = new ConcurrentLinkedQueue<>();

        /**
         * \u8c03\u7528 <code>PAID.add()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param payment \u4f20\u7ed9 <code>PAID.add()</code> \u7684 <code>TradeOrderPaymentConfirmed</code> \u8f93\u5165
         */
        public void onPayment(@Observes TradeOrderPaymentConfirmed payment) {
            PAID.add(payment);
        }
    }

    /**
     * \u8c03\u7528 <code>request()</code>\u3001<code>json.readTree()</code>\u3001<code>order.path()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param server \u4f20\u7ed9 <code>request()</code> \u7684 <code>Server</code> \u8f93\u5165
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    private void verifyProjection(Server server) throws Exception {
        request(server, "POST", "/projections/orders/T001", 200, "");
        com.fasterxml.jackson.databind.JsonNode order = json.readTree(request(server, "GET", "/orders/T001", 200, ""));
        assertEquals("CONFIRMED", order.path("orderState").asText());
        assertEquals(2500, order.path("paidAmountCent").asLong());
        assertEquals(1250, order.at("/items/0/unitPriceCent").asLong());
    }

    /**
     * \u901a\u8fc7 <code>HelidonCqrsApplication.start()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>HelidonCqrsApplication.start(0, CatalogRuntime.start(source))</code>
     */
    private Server start() {
        UnpooledDataSource source = new UnpooledDataSource(MYSQL.getDriverClassName(), MYSQL.getJdbcUrl(),
                MYSQL.getUsername(), MYSQL.getPassword());
        return HelidonCqrsApplication.start(0, CatalogRuntime.start(source));
    }

    /**
     * \u901a\u8fc7 <code>response.body()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param server \u4f20\u7ed9 <code>HttpRequest.newBuilder()</code> \u7684 <code>Server</code> \u8f93\u5165
     * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param status \u5f53\u524d HTTP \u6216\u4e1a\u52a1\u6d41\u7a0b\u7684\u72b6\u6001
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>response.body()</code>
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    private String request(Server server, String method, String path,
                           int status, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/orders/catalog" + path))
                .header("X-Tenant-Id", tenantId).header("Idempotency-Key", path.endsWith("/payment") ? "checkout-confirmation" : "").header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<java.lang.String> response = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), response.body());
        return response.body();
    }
}
