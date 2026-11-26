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

@Testcontainers
public class JavalinEntryContractTest {
    public static final String GOODS = "{\"spuName\":\"Book\",\"skuCode\":\"S1\",\"skuName\":\"Edition\",\"shopId\":7,\"salePriceCent\":1990}\n";
    public static final String ORDER = "{\"shopId\":7,\"items\":[{\"skuCode\":\"S1\",\"quantity\":2}]}\n";
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    public static CatalogEndpoints catalog(MySQLContainer<?> database) {
        return CatalogRuntime.start(new UnpooledDataSource(database.getDriverClassName(), database.getJdbcUrl(),
                database.getUsername(), database.getPassword()));
    }

    private static SampleHttpTransport.Response request(JavalinSample.JavalinApplication app, String method,
                                                        String path, String body) throws Exception {
        return SampleHttpTransport.request("http://127.0.0.1:" + app.app().port() + "/api" + path,
                method, ImmutableMap.of(), ImmutableMap.of("X-Tenant-Id", "basic"), body);
    }

    private static void assertBindFailure(RuntimeException failure) {
        Throwable cause = failure;
        while (Objects.nonNull(cause.getCause())) {
            cause = cause.getCause();
        }
        assertTrue(cause instanceof BindException, "Original occupied-port failure must propagate: " + failure);
    }

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
