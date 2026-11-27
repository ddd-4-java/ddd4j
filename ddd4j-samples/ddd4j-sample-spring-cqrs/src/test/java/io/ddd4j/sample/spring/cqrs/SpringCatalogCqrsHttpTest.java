package io.ddd4j.sample.spring.cqrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.sample.order.domain.event.TradeOrderPaymentConfirmed;
import io.ddd4j.spring.command.SpringCommandBus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.PayloadApplicationEvent;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * 通过 <code>start()</code>、<code>app.addApplicationListener()</code>、<code>payload.getPayload()</code> 验证 <code>SpringCatalogCqrsHttp</code> 的运行行为。
 */
@Testcontainers
class SpringCatalogCqrsHttpTest {
    /**
     * 为当前集成测试提供独立 MySQL 服务的容器。
     */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    /**
     * 发送合同测试请求的 HTTP 客户端。
     */
    private final HttpClient http = HttpClient.newHttpClient();
    /**
     * 负责转换测试或协议正文中 JSON 数据的 Jackson 映射器。
     */
    private final ObjectMapper json = new ObjectMapper();
    /**
     * 业务数据或认证请求所属的测试租户。
     */
    private final String tenant = UUID.randomUUID().toString().substring(0, 20);

    /**
     * 验证<code>app.getBeansOfType(CommandBus.class).size()</code> 等于 <code>1</code>，<code>events.size()</code> 等于 <code>1</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
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

    /**
     * 调用 <code>expect()</code>、<code>json.readTree()</code>、<code>order.path()</code> 执行当前夹具操作。
     *
     * @param app 传给 <code>expect()</code> 的 <code>ServletWebServerApplicationContext</code> 输入
     * @throws Exception 处理测试数据的序列化时失败
     */
    private void verifyProjection(ServletWebServerApplicationContext app) throws Exception {
        expect(app, "POST", "/orders/catalog/projections/orders/T001", null, 200);
        var order = json.readTree(expect(app, "GET", "/orders/catalog/orders/T001", null, 200));
        assertEquals("CONFIRMED", order.path("orderState").asText());
        assertEquals(2500, order.path("paidAmountCent").asLong());
        assertEquals(1250, order.at("/items/0/unitPriceCent").asLong());
    }

    /**
     * 调用 <code>SpringApplication.run()</code>、<code>MYSQL.getJdbcUrl()</code>、<code>MYSQL.getUsername()</code> 执行当前夹具操作。
     *
     * @return 提供给后续契约断言的 <code>ServletWebServerApplicationContext</code> 结果
     */
    private ServletWebServerApplicationContext start() {
        return (ServletWebServerApplicationContext) SpringApplication.run(SpringCqrsSampleApplication.class,
                "--spring.profiles.active=catalog-trade", "--server.port=0", "--CATALOG_JDBC_URL=" + MYSQL.getJdbcUrl(),
                "--CATALOG_DB_USER=" + MYSQL.getUsername(), "--CATALOG_DB_PASSWORD=" + MYSQL.getPassword());
    }

    /**
     * 通过 <code>response.body()</code> 取得当前测试需要的结果。
     *
     * @param app    传给 <code>HttpRequest.newBuilder()</code> 的 <code>ServletWebServerApplicationContext</code> 输入
     * @param method 发给被测服务的 HTTP 方法
     * @param path   测试请求的资源路径或待访问文件路径
     * @param body   当前请求或响应中要读取的正文
     * @param status 当前 HTTP 或业务流程的状态
     * @return 当前测试使用的结果，取自 <code>response.body()</code>
     * @throws Exception 发起测试请求时失败
     */
    private String expect(ServletWebServerApplicationContext app, String method, String path,
                          String body, int status) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.getWebServer().getPort() + path))
                .header("X-Tenant-Id", tenant).header("Idempotency-Key", path.endsWith("/payment") ? "checkout-confirmation" : "")
                .header("Content-Type", "application/json")
                .method(method, java.util.Objects.isNull(body) ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body)).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), response.body());
        return response.body();
    }
}
