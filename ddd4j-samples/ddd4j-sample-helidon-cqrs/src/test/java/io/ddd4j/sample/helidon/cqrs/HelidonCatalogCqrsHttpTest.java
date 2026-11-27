package io.ddd4j.sample.helidon.cqrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.order.domain.event.TradeOrderPaymentConfirmed;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.helidon.microprofile.server.Server;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
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
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 通过 <code>start()</code>、<code>request()</code>、<code>TradeEventObserver.PAID.size()</code> 验证 <code>HelidonCatalogCqrsHttp</code> 的运行行为。
 */
@Testcontainers
class HelidonCatalogCqrsHttpTest {
    /**
     * 为当前集成测试提供独立 MySQL 服务的容器。
     */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    /**
     * 当前业务数据所属的租户标识。
     */
    private final String tenantId = UUID.randomUUID().toString().substring(0, 20);
    /**
     * 发送合同测试请求的 HTTP 客户端。
     */
    private final HttpClient http = HttpClient.newHttpClient();
    /**
     * 负责转换测试或协议正文中 JSON 数据的 Jackson 映射器。
     */
    private final ObjectMapper json = new ObjectMapper();

    /**
     * 验证<code>TradeEventObserver.PAID.size()</code> 等于 <code>1</code>，<code>TradeEventObserver.PAID.element().getTenantId()</code> 等于 <code>tenantId</code>。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void nativeServerRebuildsPaymentSnapshotAfterRestart() throws Exception {
        Server server = start();
        try {
            request(server, "POST", "/goods/LUNCH", 201, """
                    {"spuName":"Lunch","skuCode":"S1","skuName":"Weekday","salePriceCent":1250,"shopId":7}
                    """);
            request(server, "POST", "/orders/T001", 201, """
                    {"shopId":7,"items":[{"skuCode":"S1","quantity":2}]}
                    """);
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

    /**
     * 调用 <code>request()</code>、<code>json.readTree()</code>、<code>order.path()</code> 执行当前夹具操作。
     *
     * @param server 传给 <code>request()</code> 的 <code>Server</code> 输入
     * @throws Exception 处理测试数据的序列化时失败
     */
    private void verifyProjection(Server server) throws Exception {
        request(server, "POST", "/projections/orders/T001", 200, "");
        var order = json.readTree(request(server, "GET", "/orders/T001", 200, ""));
        assertEquals("CONFIRMED", order.path("orderState").asText());
        assertEquals(2500, order.path("paidAmountCent").asLong());
        assertEquals(1250, order.at("/items/0/unitPriceCent").asLong());
    }

    /**
     * 通过 <code>HelidonCqrsApplication.start()</code> 取得当前测试需要的结果。
     *
     * @return 当前测试使用的结果，取自 <code>HelidonCqrsApplication.start(0, CatalogRuntime.start(source))</code>
     */
    private Server start() {
        var source = new UnpooledDataSource(MYSQL.getDriverClassName(), MYSQL.getJdbcUrl(),
                MYSQL.getUsername(), MYSQL.getPassword());
        return HelidonCqrsApplication.start(0, CatalogRuntime.start(source));
    }

    /**
     * 通过 <code>response.body()</code> 取得当前测试需要的结果。
     *
     * @param server 传给 <code>HttpRequest.newBuilder()</code> 的 <code>Server</code> 输入
     * @param method 发给被测服务的 HTTP 方法
     * @param path   测试请求的资源路径或待访问文件路径
     * @param status 当前 HTTP 或业务流程的状态
     * @param body   当前请求或响应中要读取的正文
     * @return 当前测试使用的结果，取自 <code>response.body()</code>
     * @throws Exception 发起测试请求时失败
     */
    private String request(Server server, String method, String path,
                           int status, String body) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/orders/catalog" + path))
                .header("X-Tenant-Id", tenantId).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        var response = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), response.body());
        return response.body();
    }

    /**
     * 实现 <code>Extension</code>，供测试调用 <code>install()</code> 并观察结果。
     */
    public static final class TradeEventObserverExtension implements Extension {
        /**
         * 调用 <code>discovery.addAnnotatedType()</code>、<code>manager.createAnnotatedType()</code> 执行当前夹具操作。
         *
         * @param discovery 传给 <code>discovery.addAnnotatedType()</code> 的 <code>BeforeBeanDiscovery</code> 输入
         * @param manager   传给 <code>discovery.addAnnotatedType()</code> 的 <code>BeanManager</code> 输入
         */
        public void install(@Observes BeforeBeanDiscovery discovery, BeanManager manager) {
            discovery.addAnnotatedType(manager.createAnnotatedType(TradeEventObserver.class), "catalog-trade-event-test");
        }
    }

    /**
     * 向测试容器注册 <code>onPayment()</code> 等测试入口或组件。
     */
    @ApplicationScoped
    public static class TradeEventObserver {
        /**
         * 表示测试订单已经完成付款的状态值。
         */
        static final ConcurrentLinkedQueue<TradeOrderPaymentConfirmed> PAID = new ConcurrentLinkedQueue<>();

        /**
         * 调用 <code>PAID.add()</code> 执行当前夹具操作。
         *
         * @param payment 传给 <code>PAID.add()</code> 的 <code>TradeOrderPaymentConfirmed</code> 输入
         */
        public void onPayment(@Observes TradeOrderPaymentConfirmed payment) {
            PAID.add(payment);
        }
    }
}
