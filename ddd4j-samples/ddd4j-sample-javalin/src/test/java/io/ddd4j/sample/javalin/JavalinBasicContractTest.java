package io.ddd4j.sample.javalin;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.sample.order.testkit.SampleHttpTransport;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 无认证基础入口的真实 HTTP 行为门禁。
 */
@Testcontainers
class JavalinBasicContractTest {
    /**
     * 为当前集成测试提供独立 MySQL 服务的容器。
     */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    /**
     * 验证<code>app.token()</code> 等于 <code>""</code>，<code>result.statusCode()</code> 等于 <code>201</code>。
     *
     * @throws Exception 发起测试请求时失败
     */
    @Test
    void offersAnUnauthenticatedBasicLifecycle() throws Exception {
        try (JavalinSample.JavalinApplication app = JavalinSample.start(0, JavalinEntryContractTest.catalog(MYSQL), false)) {
            assertEquals("", app.token());
            var result = SampleHttpTransport.request("http://127.0.0.1:" + app.app().port() + "/api/goods/BOOK",
                    "POST", Map.of(), Map.of("X-Tenant-Id", "basic-lifecycle"), JavalinEntryContractTest.GOODS);
            assertEquals(201, result.statusCode(), result.body());
        }
    }

    /**
     * 验证<code>ThreadContext.getResources()</code> 等于 <code>expected</code>，<code>ThreadContext.getValues()</code> 等于 <code>expected</code>。
     */
    @Test
    void closePreservesCallingThreadsCompleteContext() {
        try (ThreadContext.Scope original = ThreadContext.open()) {
            ThreadContext.put(new Object(), new Object());
            ThreadContext.set("caller-value", "preserved");
            Map<Object, Object> expected = ThreadContext.getResources();
            JavalinSample.start(0, JavalinEntryContractTest.catalog(MYSQL), false).close();
            assertEquals(expected, ThreadContext.getResources());
            assertEquals(expected, ThreadContext.getValues());
        }
    }
}
