package io.ddd4j.sample.quarkus;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证实际基础 HTTP 入口无需令牌、命令执行器或 Quartz。
 */
@QuarkusTest
@QuarkusTestResource(value = CatalogDatabaseResource.class, restrictToAnnotatedClass = true)
class BasicOrderHttpTest {
    /**
     * 本地测试服务的基础地址。
     */
    @TestHTTPResource
    URI base;

    /**
     * 验证<code>response.statusCode()</code> 等于 <code>404</code>。
     *
     * @throws Exception 发起测试请求时失败
     */
    @Test
    void sharedOrderIsDirectlyCallableWithoutAuthentication() throws Exception {
        var request = HttpRequest.newBuilder(base.resolve("/api/orders/missing"))
                .header("X-Tenant-Id", "quarkus-basic").GET().build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode(), response.body());
    }

    /**
     * 验证<code>Class.forName("org.quartz.Scheduler")</code> 抛出 <code>ClassNotFoundException.class</code>。
     */
    @Test
    void quartzIsNotPartOfBasicApplication() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("org.quartz.Scheduler"));
    }
}
