package io.ddd4j.sample.quarkus;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import io.ddd4j.sample.order.testkit.http.HttpClient;
import io.ddd4j.sample.order.testkit.http.HttpRequest;
import io.ddd4j.sample.order.testkit.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证实际基础 HTTP 入口无需令牌、命令执行器或 Quartz。
 */
@QuarkusTest
@QuarkusTestResource(value = CatalogDatabaseResource.class)
class BasicOrderHttpTest {
    @TestHTTPResource
    URI base;

    @Test
    void sharedOrderIsDirectlyCallableWithoutAuthentication() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(base.resolve("/api/orders/missing"))
                .header("X-Tenant-Id", "quarkus-basic").GET().build();
        HttpResponse<java.lang.String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode(), response.body());
    }

    @Test
    void quartzIsNotPartOfBasicApplication() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("org.quartz.Scheduler"));
    }
}
