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
    /** \u4e3a\u5f53\u524d\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u72ec\u7acb MySQL \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    /**
     * \u9a8c\u8bc1<code>app.token()</code> \u7b49\u4e8e <code>""</code>\uff0c<code>result.statusCode()</code> \u7b49\u4e8e <code>201</code>\u3002
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
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

    /** \u9a8c\u8bc1<code>ThreadContext.getResources()</code> \u7b49\u4e8e <code>expected</code>\uff0c<code>ThreadContext.getValues()</code> \u7b49\u4e8e <code>expected</code>\u3002 */
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
