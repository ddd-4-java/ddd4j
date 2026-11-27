package io.ddd4j.sample.micronaut.cqrs;

import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.common.collect.ImmutableMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import java.net.URI;
import io.ddd4j.sample.order.testkit.http.HttpClient;
import io.ddd4j.sample.order.testkit.http.HttpRequest;
import io.ddd4j.sample.order.testkit.http.HttpResponse;
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

/** \u901a\u8fc7 <code>start()</code>\u3001<code>app.getApplicationContext()</code>\u3001<code>expect()</code> \u9a8c\u8bc1 <code>MicronautCatalogCqrsHttp</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
@Testcontainers
class MicronautCatalogCqrsHttpTest {
    /** \u4e3a\u5f53\u524d\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u72ec\u7acb MySQL \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    /** \u53d1\u9001\u5408\u540c\u6d4b\u8bd5\u8bf7\u6c42\u7684 HTTP \u5ba2\u6237\u7aef\u3002 */
    private final HttpClient http = HttpClient.newHttpClient();
    /** \u8d1f\u8d23\u8f6c\u6362\u6d4b\u8bd5\u6216\u534f\u8bae\u6b63\u6587\u4e2d JSON \u6570\u636e\u7684 Jackson \u6620\u5c04\u5668\u3002 */
    private final ObjectMapper json = new ObjectMapper();
    /** \u4e1a\u52a1\u6570\u636e\u6216\u8ba4\u8bc1\u8bf7\u6c42\u6240\u5c5e\u7684\u6d4b\u8bd5\u79df\u6237\u3002 */
    private final String tenant = UUID.randomUUID().toString().substring(0, 20);

    /**
     * \u9a8c\u8bc1<code>app.getApplicationContext().getBeansOfType(CommandBus.class).size()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>paid.size()</code> \u7b49\u4e8e <code>1</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void nativeBusCommitsPaymentAndReopenedViewRebuildsFromFourTables() throws Exception {
        try (EmbeddedServer app = start()) {
            assertEquals(1, app.getApplicationContext().getBeansOfType(CommandBus.class).size());
            assertTrue(app.getApplicationContext().getBean(CommandBus.class) instanceof DefaultCommandBus);
            expect(app, "POST", "/orders", "{}", 404);
            expect(app, "POST", "/orders/catalog/goods/LUNCH", "{\"spuName\":\"Lunch\",\"skuCode\":\"S1\",\"skuName\":\"Weekday\",\"salePriceCent\":1250,\"shopId\":7}\n", 201);
            expect(app, "POST", "/orders/catalog/orders/T001", "{\"shopId\":7,\"items\":[{\"skuCode\":\"S1\",\"quantity\":2}]}\n", 201);
            expect(app, "GET", "/orders/catalog/orders/T001", null, 404);
            expect(app, "POST", "/orders/catalog/orders/T001/payment", "{\"paidAmountCent\":2499}", 409);
            expect(app, "POST", "/orders/catalog/orders/T001/payment", "{\"paidAmountCent\":2500}", 200);
            expect(app, "POST", "/orders/catalog/orders/T001/payment", "{\"paidAmountCent\":2500}", 200);
            ConcurrentLinkedQueue<TradeOrderPaymentConfirmed> paid = app.getApplicationContext().getBean(PaidEvents.class).events;
            assertEquals(1, paid.size());
            assertEquals(tenant, paid.element().getTenantId());
            verifyProjection(app);
        }
        try (EmbeddedServer reopened = start()) {
            expect(reopened, "GET", "/orders/catalog/orders/T001", null, 404);
            verifyProjection(reopened);
        }
    }

    /** \u5b9e\u73b0 <code>ApplicationEventListener</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>onApplicationEvent()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    @Singleton
    public static class PaidEvents implements ApplicationEventListener<TradeOrderPaymentConfirmed> {
        /** \u5f53\u524d\u6d4b\u8bd5\u671f\u95f4\u6536\u96c6\u7684\u9886\u57df\u4e8b\u4ef6\uff0c\u4f9b\u65ad\u8a00\u6bd4\u8f83\u53d1\u5e03\u5185\u5bb9\u6216\u987a\u5e8f\u3002 */
        final ConcurrentLinkedQueue<TradeOrderPaymentConfirmed> events = new ConcurrentLinkedQueue<>();

        /**
         * \u8c03\u7528 <code>events.add()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param event \u4ea4\u7ed9\u88ab\u6d4b\u5165\u53e3\u5904\u7406\u7684\u6d4b\u8bd5\u9886\u57df\u4e8b\u4ef6
         */
        @Override
        public void onApplicationEvent(TradeOrderPaymentConfirmed event) {
            events.add(event);
        }
    }

    /**
     * \u8c03\u7528 <code>expect()</code>\u3001<code>json.readTree()</code>\u3001<code>order.path()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param app \u4f20\u7ed9 <code>expect()</code> \u7684 <code>EmbeddedServer</code> \u8f93\u5165
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    private void verifyProjection(EmbeddedServer app) throws Exception {
        expect(app, "POST", "/orders/catalog/projections/orders/T001", null, 200);
        com.fasterxml.jackson.databind.JsonNode order = json.readTree(expect(app, "GET", "/orders/catalog/orders/T001", null, 200));
        assertEquals("CONFIRMED", order.path("orderState").asText());
        assertEquals(2500, order.path("paidAmountCent").asLong());
        assertEquals(1250, order.at("/items/0/unitPriceCent").asLong());
    }

    /**
     * \u8c03\u7528 <code>ApplicationContext.run()</code>\u3001<code>MYSQL.getJdbcUrl()</code>\u3001<code>MYSQL.getUsername()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>EmbeddedServer</code> \u7ed3\u679c
     */
    private EmbeddedServer start() {
        return ApplicationContext.run(EmbeddedServer.class, ImmutableMap.of("micronaut.server.port", -1,
                "catalog.jdbc.url", MYSQL.getJdbcUrl(), "catalog.db.user", MYSQL.getUsername(),
                "catalog.db.password", MYSQL.getPassword()));
    }

    /**
     * \u901a\u8fc7 <code>response.body()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param app \u4f20\u7ed9 <code>HttpRequest.newBuilder()</code> \u7684 <code>EmbeddedServer</code> \u8f93\u5165
     * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @param status \u5f53\u524d HTTP \u6216\u4e1a\u52a1\u6d41\u7a0b\u7684\u72b6\u6001
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>response.body()</code>
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    private String expect(EmbeddedServer app, String method, String path,
                          String body, int status) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.getPort() + path))
                .header("X-Tenant-Id", tenant)
                .header("Content-Type", "application/json")
                .method(method, java.util.Objects.isNull(body) ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<java.lang.String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), response.body());
        return response.body();
    }
}
