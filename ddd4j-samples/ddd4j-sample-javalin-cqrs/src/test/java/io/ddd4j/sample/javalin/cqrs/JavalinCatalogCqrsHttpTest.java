package io.ddd4j.sample.javalin.cqrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import io.ddd4j.sample.order.domain.event.TradeOrderPaymentConfirmed;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.javalin.Javalin;
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
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 通过 <code>start()</code>、<code>request()</code>、<code>payments.size()</code> 验证 <code>JavalinCatalogCqrsHttp</code> 的运行行为。
 */
@Testcontainers
class JavalinCatalogCqrsHttpTest {
    /**
     * 为当前集成测试提供独立 MySQL 服务的容器。
     */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    /**
     * 保存当前夹具中的付款事件或支付数据。
     */
    private final CopyOnWriteArrayList<TradeOrderPaymentConfirmed> payments = new CopyOnWriteArrayList<>();
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
     * 验证<code>payments.size()</code> 等于 <code>1</code>，<code>payments.get(0).getTenantId()</code> 等于 <code>tenantId</code>。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void nativeServerRebuildsPaymentSnapshotAfterRestart() throws Exception {
        Javalin server = start();
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
            assertEquals(1, payments.size());
            assertEquals(tenantId, payments.get(0).getTenantId());
            verifyProjection(server);
        } finally {
            server.stop();
        }
        Javalin reopened = start();
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
     * @param server 传给 <code>request()</code> 的 <code>Javalin</code> 输入
     * @throws Exception 处理测试数据的序列化时失败
     */
    private void verifyProjection(Javalin server) throws Exception {
        request(server, "POST", "/projections/orders/T001", 200, "");
        var order = json.readTree(request(server, "GET", "/orders/T001", 200, ""));
        assertEquals("CONFIRMED", order.path("orderState").asText());
        assertEquals(2500, order.path("paidAmountCent").asLong());
        assertEquals(1250, order.at("/items/0/unitPriceCent").asLong());
    }

    /**
     * 通过 <code>JavalinCqrsApplication.startCatalog()</code> 取得当前测试需要的结果。
     *
     * @return 当前测试使用的结果，取自 <code>JavalinCqrsApplication.startCatalog(0, (publisher)-&gt;CatalogRuntime.start(source, publisher), events)</code>
     */
    private Javalin start() {
        var source = new UnpooledDataSource(MYSQL.getDriverClassName(), MYSQL.getJdbcUrl(),
                MYSQL.getUsername(), MYSQL.getPassword());
        var events = new EventBus();
        events.register(this);
        return JavalinCqrsApplication.startCatalog(0, publisher -> CatalogRuntime.start(source, publisher), events);
    }

    /**
     * 调用 <code>payments.add()</code> 执行当前夹具操作。
     *
     * @param event 交给被测入口处理的测试领域事件
     */
    @Subscribe
    public void observedPayment(TradeOrderPaymentConfirmed event) {
        payments.add(event);
    }

    /**
     * 通过 <code>response.body()</code> 取得当前测试需要的结果。
     *
     * @param server 传给 <code>HttpRequest.newBuilder()</code> 的 <code>Javalin</code> 输入
     * @param method 发给被测服务的 HTTP 方法
     * @param path   测试请求的资源路径或待访问文件路径
     * @param status 当前 HTTP 或业务流程的状态
     * @param body   当前请求或响应中要读取的正文
     * @return 当前测试使用的结果，取自 <code>response.body()</code>
     * @throws Exception 发起测试请求时失败
     */
    private String request(Javalin server, String method, String path,
                           int status, String body) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/orders/catalog" + path))
                .header("X-Tenant-Id", tenantId).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        var response = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), response.body());
        return response.body();
    }
}
