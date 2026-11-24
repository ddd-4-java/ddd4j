package io.ddd4j.sample.javalin;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.sample.order.testkit.EnvelopeOrderHttpDriver;
import io.ddd4j.sample.order.testkit.SampleContractAssertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 无认证基础入口的真实 HTTP 行为门禁。 */
class JavalinBasicContractTest {

    @Test
    void offersAnUnauthenticatedBasicLifecycle() {
        try (JavalinSample.JavalinApplication app = JavalinSample.startBasic(0)) {
            assertEquals("", app.token());
            SampleContractAssertions.defaultOrder(new EnvelopeOrderHttpDriver(
                    "http://127.0.0.1:" + app.app().port(), "/api/orders", Collections.emptyMap()),
                    "BASIC-" + UUID.randomUUID());
        }
    }

    @Test
    void closePreservesCallingThreadsCompleteContext() {
        try (ThreadContext.Scope original = ThreadContext.open()) {
            ThreadContext.put(new Object(), new Object());
            ThreadContext.set("caller-value", "preserved");
            Map<Object, Object> expected = ThreadContext.getResources();
            JavalinSample.startBasic(0).close();
            assertEquals(expected, ThreadContext.getResources());
            assertEquals(expected, ThreadContext.getValues());
        }
    }
}
