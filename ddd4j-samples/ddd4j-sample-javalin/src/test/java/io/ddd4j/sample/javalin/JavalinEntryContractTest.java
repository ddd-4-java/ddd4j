package io.ddd4j.sample.javalin;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.sample.order.start.CatalogRuntime;
import io.ddd4j.sample.order.testkit.SampleHttpTransport;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.BindException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通过 <code>JavalinSample.start()</code>、<code>catalog()</code>、<code>request()</code> 验证 <code>JavalinEntryContract</code> 的运行行为。
 */
@Testcontainers
public class JavalinEntryContractTest {
    /**
     * 商品测试所使用的路由或业务编码。
     */
    public static final String GOODS = """
            {"spuName":"Book","skuCode":"S1","skuName":"Edition","shopId":7,"salePriceCent":1990}
            """;
    /**
     * 测试订单的固定标识，用于组织和查询测试数据。
     */
    public static final String ORDER = """
            {"shopId":7,"items":[{"skuCode":"S1","quantity":2}]}
            """;
    /**
     * 为当前集成测试提供独立 MySQL 服务的容器。
     */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    /**
     * 调用 <code>CatalogRuntime.start()</code>、<code>database.getDriverClassName()</code>、<code>database.getJdbcUrl()</code> 执行当前夹具操作。
     *
     * @param database 传给 <code>CatalogRuntime.start()</code> 的 <code>MySQLContainer&lt;?&gt;</code> 输入
     * @return 提供给后续契约断言的 <code>CatalogEndpoints</code> 结果
     */
    public static CatalogEndpoints catalog(MySQLContainer<?> database) {
        return CatalogRuntime.start(new UnpooledDataSource(database.getDriverClassName(), database.getJdbcUrl(),
                database.getUsername(), database.getPassword()));
    }

    /**
     * 调用 <code>SampleHttpTransport.request()</code>、<code>app.app()</code> 执行当前夹具操作。
     *
     * @param app    传给 <code>SampleHttpTransport.request()</code> 的 <code>JavalinSample.JavalinApplication</code> 输入
     * @param method 发给被测服务的 HTTP 方法
     * @param path   测试请求的资源路径或待访问文件路径
     * @param body   当前请求或响应中要读取的正文
     * @return 提供给后续契约断言的 <code>SampleHttpTransport.Response</code> 结果
     * @throws Exception 发起测试请求时失败
     */
    private static SampleHttpTransport.Response request(JavalinSample.JavalinApplication app, String method,
                                                        String path, String body) throws Exception {
        return SampleHttpTransport.request("http://127.0.0.1:" + app.app().port() + "/api" + path,
                method, Map.of(), Map.of("X-Tenant-Id", "basic", "Idempotency-Key", path.endsWith("/payment") ? "checkout-confirmation" : ""), body);
    }

    /**
     * 核对<code>cause instanceof BindException</code>为真。
     *
     * @param failure 触发当前测试错误处理路径的失败
     */
    private static void assertBindFailure(RuntimeException failure) {
        Throwable cause = failure;
        while (Objects.nonNull(cause.getCause())) {
            cause = cause.getCause();
        }
        assertTrue(cause instanceof BindException, "Original occupied-port failure must propagate: " + failure);
    }

    /**
     * 验证<code>request(app, "POST", "/goods/BOOK", GOODS).statusCode()</code> 等于 <code>201</code>，<code>request(app, "POST", "/orders/BASIC", ORDER).statusCode()</code> 等于 <code>201</code>，<code>order.statusCode()</code> 等于 <code>200</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
     */
    @Test
    void anonymousEntryPersistsOrdersAcrossRestarts() throws Exception {
        try (var app = JavalinSample.start(0, catalog(MYSQL), false)) {
            assertEquals(201, request(app, "POST", "/goods/BOOK", GOODS).statusCode());
            assertEquals(201, request(app, "POST", "/orders/BASIC", ORDER).statusCode());
            assertEquals(200, request(app, "POST", "/orders/BASIC/payment", "{\"paidAmountCent\":3980}").statusCode());
        }
        try (var app = JavalinSample.start(0, catalog(MYSQL), false)) {
            var order = request(app, "GET", "/orders/BASIC", null);
            assertEquals(200, order.statusCode(), order.body());
            assertEquals(3980, new ObjectMapper().readTree(order.body()).path("paidAmountCent").asLong());
        }
    }

    /**
     * 验证<code>{ JavalinSample.start(occupied.getLocalPort(), catalog(MYSQL), false).close(); }</code> 抛出 <code>RuntimeException.class</code>，<code>ThreadContext.getValues()</code> 等于 <code>expected</code>。
     *
     * @throws Exception 准备或释放被测运行资源时失败
     */
    @Test
    void failedBasicStartupPreservesCallerContextAndBindFailure() throws Exception {
        try (ThreadContext.Scope original = ThreadContext.open();
             ServerSocket occupied = new ServerSocket(0)) {
            ThreadContext.put(new Object(), new Object());
            ThreadContext.set("caller-value", "preserved");
            Map<Object, Object> expected = ThreadContext.getResources();
            RuntimeException failure = assertThrows(RuntimeException.class,
                    () -> {
                        JavalinSample.start(occupied.getLocalPort(), catalog(MYSQL), false).close();
                    });
            assertBindFailure(failure);
            assertEquals(expected, ThreadContext.getResources());
            assertEquals(expected, ThreadContext.getValues());
        }
    }

}
