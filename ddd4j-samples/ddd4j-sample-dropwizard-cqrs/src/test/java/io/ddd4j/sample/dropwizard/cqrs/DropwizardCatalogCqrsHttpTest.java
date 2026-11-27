package io.ddd4j.sample.dropwizard.cqrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.order.domain.event.TradeOrderPaymentConfirmed;
import io.dropwizard.core.Application;
import io.dropwizard.core.server.DefaultServerFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.testing.DropwizardTestSupport;
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
 * 通过 <code>application()</code>、<code>app.before()</code>、<code>expect()</code> 验证 <code>DropwizardCatalogCqrsHttp</code> 的运行行为。
 */
@Testcontainers
class DropwizardCatalogCqrsHttpTest {
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
     * 记录样例订单是否已经确认付款。
     */
    private final ConcurrentLinkedQueue<TradeOrderPaymentConfirmed> paid = new ConcurrentLinkedQueue<>();

    /**
     * 验证<code>paid.size()</code> 等于 <code>1</code>，<code>paid.element().getTenantId()</code> 等于 <code>tenant</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void nativeServerRebuildsPaidOrderFromMySqlOnRestart() throws Exception {
        var app = application();
        try {
            app.before();
            expect(app, "POST", "/goods/LUNCH", 201, """
                    {"spuName":"Lunch","skuCode":"S1","skuName":"Weekday","salePriceCent":1250,"shopId":7}
                    """);
            expect(app, "POST", "/orders/T001", 201, """
                    {"shopId":7,"items":[{"skuCode":"S1","quantity":2}]}
                    """);
            expect(app, "GET", "/orders/T001", 404, "");
            expect(app, "POST", "/orders/T001/payment", 200, "{\"paidAmountCent\":2500}");
            expect(app, "POST", "/orders/T001/payment", 200, "{\"paidAmountCent\":2500}");
            assertEquals(1, paid.size());
            assertEquals(tenant, paid.element().getTenantId());
            projectAndCheck(app);
        } finally {
            app.after();
        }
        var reopened = application();
        try {
            reopened.before();
            expect(reopened, "GET", "/orders/T001", 404, "");
            projectAndCheck(reopened);
        } finally {
            reopened.after();
        }
    }

    /**
     * 调用 <code>expect()</code>、<code>json.readTree()</code>、<code>order.path()</code> 执行当前夹具操作。
     *
     * @param app 传给 <code>expect()</code> 的 <code>DropwizardTestSupport&lt;DropwizardCqrsConfiguration&gt;</code> 输入
     * @throws Exception 处理测试数据的序列化时失败
     */
    private void projectAndCheck(DropwizardTestSupport<DropwizardCqrsConfiguration> app) throws Exception {
        expect(app, "POST", "/projections/orders/T001", 200, "");
        var order = json.readTree(expect(app, "GET", "/orders/T001", 200, ""));
        assertEquals("CONFIRMED", order.path("orderState").asText());
        assertEquals(2500, order.path("paidAmountCent").asLong());
    }

    /**
     * 调用 <code>config.setCatalogJdbcUrl()</code>、<code>MYSQL.getJdbcUrl()</code>、<code>config.setCatalogDbUser()</code> 执行当前夹具操作。
     *
     * @return 提供给后续契约断言的 <code>DropwizardTestSupport&lt;DropwizardCqrsConfiguration&gt;</code> 结果
     */
    private DropwizardTestSupport<DropwizardCqrsConfiguration> application() {
        var config = new DropwizardCqrsConfiguration();
        config.setCatalogJdbcUrl(MYSQL.getJdbcUrl());
        config.setCatalogDbUser(MYSQL.getUsername());
        config.setCatalogDbPassword(MYSQL.getPassword());
        var server = (DefaultServerFactory) config.getServerFactory();
        ((HttpConnectorFactory) server.getApplicationConnectors().get(0)).setPort(0);
        ((HttpConnectorFactory) server.getAdminConnectors().get(0)).setPort(0);
        return new DropwizardTestSupport<>(DropwizardCqrsApplication.class, config) {
            @Override
            public Application<DropwizardCqrsConfiguration> newApplication() {
                return new DropwizardCqrsApplication(event -> {
                    if (event instanceof TradeOrderPaymentConfirmed payment) {
                        paid.add(payment);
                    }
                });
            }
        };
    }

    /**
     * 通过 <code>response.body()</code> 取得当前测试需要的结果。
     *
     * @param app    传给 <code>HttpRequest.newBuilder()</code> 的 <code>DropwizardTestSupport&lt;DropwizardCqrsConfiguration&gt;</code> 输入
     * @param method 发给被测服务的 HTTP 方法
     * @param path   测试请求的资源路径或待访问文件路径
     * @param status 当前 HTTP 或业务流程的状态
     * @param body   当前请求或响应中要读取的正文
     * @return 当前测试使用的结果，取自 <code>response.body()</code>
     * @throws Exception 发起测试请求时失败
     */
    private String expect(DropwizardTestSupport<DropwizardCqrsConfiguration> app, String method,
                          String path, int status, String body) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.getLocalPort() + "/orders/catalog" + path))
                .header("X-Tenant-Id", tenant).header("Idempotency-Key", path.endsWith("/payment") ? "checkout-confirmation" : "").header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        var response = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), response.body());
        return response.body();
    }
}
