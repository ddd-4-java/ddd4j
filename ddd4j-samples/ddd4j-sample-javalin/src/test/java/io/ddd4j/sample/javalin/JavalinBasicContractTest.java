package io.ddd4j.sample.javalin;

import com.google.common.collect.ImmutableMap;
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
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    @Test
    void offersAnUnauthenticatedBasicLifecycle() throws Exception {
        try (JavalinSample.JavalinApplication app = JavalinSample.start(0, JavalinEntryContractTest.catalog(MYSQL), false)) {
            assertEquals("", app.token());
            SampleHttpTransport.Response result = SampleHttpTransport.request("http://127.0.0.1:" + app.app().port() + "/api/goods/BOOK",
                    "POST", ImmutableMap.of(), ImmutableMap.of("X-Tenant-Id", "basic-lifecycle"), JavalinEntryContractTest.GOODS);
            assertEquals(201, result.statusCode(), result.body());
        }
    }

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
