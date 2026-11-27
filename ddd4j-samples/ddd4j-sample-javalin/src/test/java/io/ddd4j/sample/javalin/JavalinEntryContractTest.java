package io.ddd4j.sample.javalin;

import com.google.common.collect.ImmutableMap;
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

/** \u901a\u8fc7 <code>JavalinSample.start()</code>\u3001<code>catalog()</code>\u3001<code>request()</code> \u9a8c\u8bc1 <code>JavalinEntryContract</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
@Testcontainers
public class JavalinEntryContractTest {
    /** \u5546\u54c1\u6d4b\u8bd5\u6240\u4f7f\u7528\u7684\u8def\u7531\u6216\u4e1a\u52a1\u7f16\u7801\u3002 */
    public static final String GOODS = "{\"spuName\":\"Book\",\"skuCode\":\"S1\",\"skuName\":\"Edition\",\"shopId\":7,\"salePriceCent\":1990}\n";
    /** \u6d4b\u8bd5\u8ba2\u5355\u7684\u56fa\u5b9a\u6807\u8bc6\uff0c\u7528\u4e8e\u7ec4\u7ec7\u548c\u67e5\u8be2\u6d4b\u8bd5\u6570\u636e\u3002 */
    public static final String ORDER = "{\"shopId\":7,\"items\":[{\"skuCode\":\"S1\",\"quantity\":2}]}\n";
    /** \u4e3a\u5f53\u524d\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u72ec\u7acb MySQL \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    /**
     * \u8c03\u7528 <code>CatalogRuntime.start()</code>\u3001<code>database.getDriverClassName()</code>\u3001<code>database.getJdbcUrl()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param database \u4f20\u7ed9 <code>CatalogRuntime.start()</code> \u7684 <code>MySQLContainer&lt;?&gt;</code> \u8f93\u5165
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>CatalogEndpoints</code> \u7ed3\u679c
     */
    public static CatalogEndpoints catalog(MySQLContainer<?> database) {
        return CatalogRuntime.start(new UnpooledDataSource(database.getDriverClassName(), database.getJdbcUrl(),
                database.getUsername(), database.getPassword()));
    }

    /**
     * \u8c03\u7528 <code>SampleHttpTransport.request()</code>\u3001<code>app.app()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param app \u4f20\u7ed9 <code>SampleHttpTransport.request()</code> \u7684 <code>JavalinSample.JavalinApplication</code> \u8f93\u5165
     * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>SampleHttpTransport.Response</code> \u7ed3\u679c
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    private static SampleHttpTransport.Response request(JavalinSample.JavalinApplication app, String method,
                                                        String path, String body) throws Exception {
        return SampleHttpTransport.request("http://127.0.0.1:" + app.app().port() + "/api" + path,
                method, ImmutableMap.of(), ImmutableMap.of("X-Tenant-Id", "basic"), body);
    }

    /**
     * \u6838\u5bf9<code>cause instanceof BindException</code>\u4e3a\u771f\u3002
     * @param failure \u89e6\u53d1\u5f53\u524d\u6d4b\u8bd5\u9519\u8bef\u5904\u7406\u8def\u5f84\u7684\u5931\u8d25
     */
    private static void assertBindFailure(RuntimeException failure) {
        Throwable cause = failure;
        while (Objects.nonNull(cause.getCause())) {
            cause = cause.getCause();
        }
        assertTrue(cause instanceof BindException, "Original occupied-port failure must propagate: " + failure);
    }

    /**
     * \u9a8c\u8bc1<code>request(app, "POST", "/goods/BOOK", GOODS).statusCode()</code> \u7b49\u4e8e <code>201</code>\uff0c<code>request(app, "POST", "/orders/BASIC", ORDER).statusCode()</code> \u7b49\u4e8e <code>201</code>\uff0c<code>order.statusCode()</code> \u7b49\u4e8e <code>200</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    @Test
    void anonymousEntryPersistsOrdersAcrossRestarts() throws Exception {
        try (io.ddd4j.sample.javalin.JavalinSample.JavalinApplication app = JavalinSample.start(0, catalog(MYSQL), false)) {
            assertEquals(201, request(app, "POST", "/goods/BOOK", GOODS).statusCode());
            assertEquals(201, request(app, "POST", "/orders/BASIC", ORDER).statusCode());
            assertEquals(200, request(app, "POST", "/orders/BASIC/payment", "{\"paidAmountCent\":3980}").statusCode());
        }
        try (io.ddd4j.sample.javalin.JavalinSample.JavalinApplication app = JavalinSample.start(0, catalog(MYSQL), false)) {
            SampleHttpTransport.Response order = request(app, "GET", "/orders/BASIC", null);
            assertEquals(200, order.statusCode(), order.body());
            assertEquals(3980, new ObjectMapper().readTree(order.body()).path("paidAmountCent").asLong());
        }
    }

    /**
     * \u9a8c\u8bc1<code>{ JavalinSample.start(occupied.getLocalPort(), catalog(MYSQL), false).close(); }</code> \u629b\u51fa <code>RuntimeException.class</code>\uff0c<code>ThreadContext.getValues()</code> \u7b49\u4e8e <code>expected</code>\u3002
     * @throws Exception \u51c6\u5907\u6216\u91ca\u653e\u88ab\u6d4b\u8fd0\u884c\u8d44\u6e90\u65f6\u5931\u8d25
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
