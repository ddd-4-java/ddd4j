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
    /** \u672c\u5730\u6d4b\u8bd5\u670d\u52a1\u7684\u57fa\u7840\u5730\u5740\u3002 */
    @TestHTTPResource
    URI base;

    /**
     * \u9a8c\u8bc1<code>response.statusCode()</code> \u7b49\u4e8e <code>404</code>\u3002
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    @Test
    void sharedOrderIsDirectlyCallableWithoutAuthentication() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(base.resolve("/api/orders/missing"))
                .header("X-Tenant-Id", "quarkus-basic").GET().build();
        HttpResponse<java.lang.String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode(), response.body());
    }

    /** \u9a8c\u8bc1<code>Class.forName("org.quartz.Scheduler")</code> \u629b\u51fa <code>ClassNotFoundException.class</code>\u3002 */
    @Test
    void quartzIsNotPartOfBasicApplication() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("org.quartz.Scheduler"));
    }
}
