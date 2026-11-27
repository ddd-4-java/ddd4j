/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.sample.javalin.order.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.javalin.JavalinEntryContractTest;
import io.ddd4j.sample.javalin.JavalinSample;
import io.ddd4j.web.core.context.WebHeaders;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通过 <code>client.send()</code>、<code>HttpRequest.newBuilder()</code>、<code>URI.create()</code> 验证 <code>OrderController</code> 的运行行为。
 */
@Testcontainers
class OrderControllerTest {
    /**
     * 为当前集成测试提供独立 MySQL 服务的容器。
     */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");
    /**
     * 负责转换测试或协议正文中 JSON 数据的 Jackson 映射器。
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 当前用例配置并启动的应用实例。
     */
    private static JavalinSample.JavalinApplication application;
    /**
     * 发送与接收测试请求的客户端。
     */
    private static HttpClient client;
    /**
     * 当前服务的 HTTP 基础地址。
     */
    private static String baseUrl;

    /**
     * 测试执行前执行 <code>JavalinSample.start()</code>、<code>JavalinEntryContractTest.catalog()</code>、<code>HttpClient.newBuilder()</code>、<code>Duration.ofSeconds()</code> 完成资源准备。
     */
    @BeforeAll
    static void start() {
        application = JavalinSample.start(0, JavalinEntryContractTest.catalog(MYSQL), true);
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        baseUrl = "http://localhost:" + application.app().port();
    }

    /**
     * 测试结束后执行 <code>application.close()</code> 完成资源回收或状态恢复。
     */
    @AfterAll
    static void stop() {
        if (Objects.nonNull(application)) {
            application.close();
        }
    }

    /**
     * 通过 <code>client.send()</code> 取得当前测试需要的结果。
     *
     * @param path 测试请求的资源路径或待访问文件路径
     * @return 当前测试使用的结果，取自 <code>client.send(request, HttpResponse.BodyHandlers.ofString())</code>
     * @throws Exception 发起测试请求时失败
     */
    private static HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header(WebHeaders.AUTHORIZATION, "Bearer " + application.token()).header("X-Tenant-Id", "bearer")
                .GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 通过 <code>client.send()</code> 取得当前测试需要的结果。
     *
     * @param path           测试请求的资源路径或待访问文件路径
     * @param body           当前请求或响应中要读取的正文
     * @param idempotencyKey 传给 <code>Objects.nonNull()</code> 的 <code>String</code> 输入
     * @return 当前测试使用的结果，取自 <code>client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString())</code>
     * @throws Exception 发起测试请求时失败
     */
    private static HttpResponse<String> post(String path, String body, String idempotencyKey) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .header(WebHeaders.AUTHORIZATION, "Bearer " + application.token()).header("X-Tenant-Id", "bearer");
        if (Objects.nonNull(idempotencyKey)) {
            request.header(WebHeaders.IDEMPOTENCY_KEY, idempotencyKey);
        }
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 验证<code>response.statusCode()</code> 等于 <code>401</code>。
     *
     * @throws Exception 发起测试请求时失败
     */
    @Test
    void protectedRouteRequiresBearerToken() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/orders/DENIED"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
    }

    /**
     * 验证<code>post("/api/goods/BOOK", JavalinEntryContractTest.GOODS, null).statusCode()</code> 等于 <code>201</code>，<code>created.statusCode()</code> 等于 <code>201</code>，<code>JSON.readTree(repeated.body())</code> 等于 <code>JSON.readTree(first.body())</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
     */
    @Test
    void createQueryAndPaymentUseSharedTradeAggregate() throws Exception {
        assertEquals(201, post("/api/goods/BOOK", JavalinEntryContractTest.GOODS, null).statusCode());
        String path = "/api/orders/J-001";
        var created = post(path, JavalinEntryContractTest.ORDER, null);
        assertEquals(201, created.statusCode(), created.body());
        assertEquals(3980, JSON.readTree(created.body()).path("grossAmountCent").asLong());
        assertEquals(200, get(path).statusCode());
        assertEquals(400, post(path + "/payment", "{}", null).statusCode());
        var first = post(path + "/payment", "{\"paidAmountCent\":3980}", null);
        var repeated = post(path + "/payment", "{\"paidAmountCent\":3980}", null);
        assertEquals(200, first.statusCode(), first.body());
        assertEquals(200, repeated.statusCode(), repeated.body());
        assertEquals("CONFIRMED", JSON.readTree(first.body()).path("orderState").asText());
        assertEquals(JSON.readTree(first.body()), JSON.readTree(repeated.body()));
    }

    /**
     * 验证<code>StrKit.isNotBlank(firstRequestId)</code>为真，<code>StrKit.isNotBlank(secondRequestId)</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void requestIdIsReturnedAndContextDoesNotLeak() throws Exception {
        HttpResponse<String> first = get("/api/orders/MISSING");
        HttpResponse<String> second = get("/api/orders/MISSING");
        String firstRequestId = first.headers().firstValue(WebHeaders.REQUEST_ID).orElseThrow();
        String secondRequestId = second.headers().firstValue(WebHeaders.REQUEST_ID).orElseThrow();
        assertTrue(StrKit.isNotBlank(firstRequestId));
        assertTrue(StrKit.isNotBlank(secondRequestId));
        assertNotEquals(firstRequestId, secondRequestId);
    }
}
