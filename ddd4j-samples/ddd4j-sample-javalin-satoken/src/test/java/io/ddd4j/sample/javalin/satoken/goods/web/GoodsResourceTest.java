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
package io.ddd4j.sample.javalin.satoken.goods.web;

import io.ddd4j.sample.javalin.satoken.TestSupport;
import io.javalin.Javalin;
import org.junit.jupiter.api.*;

import java.net.URI;
import io.ddd4j.sample.order.testkit.http.HttpClient;
import io.ddd4j.sample.order.testkit.http.HttpRequest;
import io.ddd4j.sample.order.testkit.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GoodsResource + GoodsQueryResource 集成测试（Javalin + Guice + Sa-Token，random port）。
 *
 * <p>GoodsResource 和 GoodsQueryResource 共同提供 {@code /api/goods/*} 路由。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
class GoodsResourceTest {

    /** \u7528\u4e8e\u6784\u9020\u672c\u7ec4\u7528\u4f8b\u8bf7\u6c42\u5730\u5740\u6216\u6807\u8bc6\u7684\u516c\u5171\u524d\u7f00\u3002 */
    private static final String PREFIX = "/api/goods";

    /** \u7531\u5f53\u524d\u6d4b\u8bd5\u542f\u52a8\u548c\u5173\u95ed\u7684\u5e94\u7528\u5b9e\u4f8b\u3002 */
    private static Javalin app;
    /** \u7528\u4e8e\u53d1\u9001 HTTP \u8bf7\u6c42\u7684\u5ba2\u6237\u7aef\u3002 */
    private static HttpClient httpClient;
    /** \u5f53\u524d\u670d\u52a1\u7684 HTTP \u57fa\u7840\u5730\u5740\u3002 */
    private static String baseUrl;

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>TestSupport.start()</code>\u3001<code>app.port()</code>\u3001<code>HttpClient.newBuilder()</code>\u3001<code>Duration.ofSeconds()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeAll
    static void startApp() {
        app = TestSupport.start();
        baseUrl = "http://localhost:" + app.port();
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>app.stop()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterAll
    static void stopApp() {
        if (Objects.nonNull(app)) {
            app.stop();
        }
    }

    /**
     * \u901a\u8fc7 <code>body.substring()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>body.substring(start, end)</code>
     */
    private static String extractId(String body) {
        // body: {"code":0,"message":"ok","data":{"id":123,...}}
        int idx = body.indexOf("\"id\":");
        if (idx < 0) {
            throw new IllegalStateException("no id in body: " + body);
        }
        int start = idx + 5;
        while (start < body.length() && body.charAt(start) == ' ') {
            start++;
        }
        int end = start;
        while (end < body.length() && (Character.isDigit(body.charAt(end)) || body.charAt(end) == '-')) {
            end++;
        }
        return body.substring(start, end);
    }

    /**
     * 每个测试前重启 Javalin 并清空 sa-token 全局状态，避免上一测试遗留的 token/session 干扰。
     */
    @BeforeEach
    void resetServerState() {
        app.stop();
        app = TestSupport.start();
        baseUrl = "http://localhost:" + app.port();
        // 新服务器实例使用独立连接池，不跨重启复用旧 HTTP 连接。
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /**
     * \u8c03\u7528 <code>httpClient.send()</code>\u3001<code>HttpRequest.newBuilder()</code>\u3001<code>URI.create()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>HttpResponse&lt;String&gt;</code> \u7ed3\u679c
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    private HttpResponse<String> postJson(String path, String body) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * \u8c03\u7528 <code>httpClient.send()</code>\u3001<code>HttpRequest.newBuilder()</code>\u3001<code>URI.create()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>HttpResponse&lt;String&gt;</code> \u7ed3\u679c
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    private HttpResponse<String> putJson(String path, String body) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * \u8c03\u7528 <code>httpClient.send()</code>\u3001<code>HttpRequest.newBuilder()</code>\u3001<code>URI.create()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>HttpResponse&lt;String&gt;</code> \u7ed3\u679c
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    private HttpResponse<String> put(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .PUT(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * \u901a\u8fc7 <code>httpClient.send()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).DELETE().build(), HttpResponse.BodyHandlers.ofString())</code>
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    private HttpResponse<String> delete(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * \u901a\u8fc7 <code>httpClient.send()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(), HttpResponse.BodyHandlers.ofString())</code>
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    private HttpResponse<String> get(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    // =================== 1) create ====================

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>201</code>\uff0c<code>r.body().contains("SKU-1")</code>\u4e3a\u771f\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void create_shouldReturn201() throws Exception {
        HttpResponse<String> r = postJson(PREFIX, "{\"code\":\"SKU-1\",\"name\":\"Phone\",\"price\":999.00,\"stock\":10}");
        assertEquals(201, r.statusCode());
        assertTrue(r.body().contains("SKU-1"));
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>201</code>\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void create_zeroStock_shouldReturn201() throws Exception {
        HttpResponse<String> r = postJson(PREFIX, "{\"code\":\"SKU-2\",\"name\":\"Zero\",\"price\":1.00,\"stock\":0}");
        assertEquals(201, r.statusCode());
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>201</code>\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void multipleCreates_allShouldSucceed() throws Exception {
        for (int i = 0; i < 3; i++) {
            HttpResponse<String> r = postJson(PREFIX,
                    "{\"code\":\"SKU-M" + i + "\",\"name\":\"M\",\"price\":1.00,\"stock\":1}");
            assertEquals(201, r.statusCode());
        }
    }

    // =================== 2) update ====================

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>r.body().contains("updated")</code>\u4e3a\u771f\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void update_shouldReturn200() throws Exception {
        HttpResponse<String> create = postJson(PREFIX,
                "{\"code\":\"SKU-U1\",\"name\":\"orig\",\"price\":10.00,\"stock\":5}");
        String id = extractId(create.body());
        HttpResponse<String> r = putJson(PREFIX + "/" + id,
                "{\"name\":\"updated\",\"price\":20.00}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("updated"));
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode() &gt;= 400</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void update_unknownId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = putJson(PREFIX + "/999999999",
                "{\"name\":\"x\",\"price\":1.00}");
        assertTrue(r.statusCode() >= 400);
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode() &gt;= 400</code>\u4e3a\u771f\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void update_blankName_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> create = postJson(PREFIX,
                "{\"code\":\"SKU-BN\",\"name\":\"valid\",\"price\":1.00,\"stock\":1}");
        String id = extractId(create.body());
        HttpResponse<String> r = putJson(PREFIX + "/" + id,
                "{\"name\":\"\",\"price\":1.00}");
        assertTrue(r.statusCode() >= 400);
    }

    // =================== 3) changeStatus ====================

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>r.body().contains("ON_SALE")</code>\u4e3a\u771f\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void changeStatus_toOnSale_shouldReturn200() throws Exception {
        HttpResponse<String> create = postJson(PREFIX,
                "{\"code\":\"SKU-ST1\",\"name\":\"x\",\"price\":1.00,\"stock\":1}");
        String id = extractId(create.body());
        HttpResponse<String> r = put(PREFIX + "/" + id + "/status?status=ON_SALE");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("ON_SALE"));
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>r.body().contains("OFF_SALE")</code>\u4e3a\u771f\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void changeStatus_offSale_shouldReturn200() throws Exception {
        HttpResponse<String> create = postJson(PREFIX,
                "{\"code\":\"SKU-ST2\",\"name\":\"x\",\"price\":1.00,\"stock\":1}");
        String id = extractId(create.body());
        put(PREFIX + "/" + id + "/status?status=ON_SALE");
        HttpResponse<String> r = put(PREFIX + "/" + id + "/status?status=OFF_SALE");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("OFF_SALE"));
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode() &gt;= 400</code>\u4e3a\u771f\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void changeStatus_invalidStatus_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> create = postJson(PREFIX,
                "{\"code\":\"SKU-INV\",\"name\":\"x\",\"price\":1.00,\"stock\":1}");
        String id = extractId(create.body());
        HttpResponse<String> r = put(PREFIX + "/" + id + "/status?status=NOT_A_STATUS");
        assertTrue(r.statusCode() >= 400);
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode() &gt;= 400</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void changeStatus_unknownId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = put(PREFIX + "/999999999/status?status=ON_SALE");
        assertTrue(r.statusCode() >= 400);
    }

    // =================== 4) delete ====================

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void delete_shouldReturn200() throws Exception {
        HttpResponse<String> create = postJson(PREFIX,
                "{\"code\":\"SKU-D1\",\"name\":\"x\",\"price\":1.00,\"stock\":1}");
        String id = extractId(create.body());
        HttpResponse<String> r = delete(PREFIX + "/" + id);
        assertEquals(200, r.statusCode());
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode() &gt;= 400</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void delete_unknownId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = delete(PREFIX + "/999999999");
        assertTrue(r.statusCode() >= 400);
    }

    // =================== 5) getById ====================

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>r.body().contains("SKU-G1")</code>\u4e3a\u771f\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void getById_shouldReturnGoods() throws Exception {
        HttpResponse<String> create = postJson(PREFIX,
                "{\"code\":\"SKU-G1\",\"name\":\"Box\",\"price\":5.00,\"stock\":3}");
        String id = extractId(create.body());
        HttpResponse<String> r = get(PREFIX + "/" + id);
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("SKU-G1"));
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode() &gt;= 400</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void getById_unknownId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/999999999");
        assertTrue(r.statusCode() >= 400);
    }

    // =================== 6) getByCode ====================

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>r.body().contains("SKU-BC1")</code>\u4e3a\u771f\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void getByCode_shouldReturnGoods() throws Exception {
        postJson(PREFIX, "{\"code\":\"SKU-BC1\",\"name\":\"x\",\"price\":1.00,\"stock\":1}");
        HttpResponse<String> r = get(PREFIX + "/by-code?code=SKU-BC1");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("SKU-BC1"));
    }

    // =================== 7) pageQuery / listQuery / countQuery (在 queryResource 中) ========

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void pageQuery_defaultPage_shouldReturn200() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/page?current=1&size=10");
        assertEquals(200, r.statusCode());
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>r.body().contains("SKU-PS1")</code>\u4e3a\u771f\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void pageQuery_byStatus_shouldReturnMatchingGoods() throws Exception {
        HttpResponse<String> create = postJson(PREFIX,
                "{\"code\":\"SKU-PS1\",\"name\":\"x\",\"price\":1.00,\"stock\":1}");
        String id = extractId(create.body());
        put(PREFIX + "/" + id + "/status?status=ON_SALE");

        HttpResponse<String> r = get(PREFIX + "/page?status=ON_SALE&current=1&size=10");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("SKU-PS1"));
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>r.body().contains("SKU-PR1")</code>\u4e3a\u771f\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void pageQuery_byPriceRange_shouldReturnMatchingGoods() throws Exception {
        postJson(PREFIX, "{\"code\":\"SKU-PR1\",\"name\":\"x\",\"price\":50.00,\"stock\":1}");
        HttpResponse<String> r = get(PREFIX + "/page?priceMin=10&priceMax=100&current=1&size=10");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("SKU-PR1"));
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>r.body().contains("SKU-NL1")</code>\u4e3a\u771f\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void pageQuery_byNameLike_shouldReturnMatchingGoods() throws Exception {
        postJson(PREFIX, "{\"code\":\"SKU-NL1\",\"name\":\"iPhoneX\",\"price\":1.00,\"stock\":1}");
        HttpResponse<String> r = get(PREFIX + "/page?nameLike=iPhone&current=1&size=10");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("SKU-NL1"));
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>r.body().contains("SKU-EX1")</code>\u4e3a\u771f\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void pageQuery_byCode_shouldReturnMatchingGoods() throws Exception {
        postJson(PREFIX, "{\"code\":\"SKU-EX1\",\"name\":\"x\",\"price\":1.00,\"stock\":1}");
        HttpResponse<String> r = get(PREFIX + "/page?code=SKU-EX1&current=1&size=10");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("SKU-EX1"));
    }

    /**
     * \u9a8c\u8bc1<code>p1.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>p3.statusCode()</code> \u7b49\u4e8e <code>200</code>\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void pageQuery_withPagination_shouldReturn200() throws Exception {
        for (int i = 0; i < 15; i++) {
            postJson(PREFIX, "{\"code\":\"SKU-PG" + i + "\",\"name\":\"P\",\"price\":1.00,\"stock\":1}");
        }
        HttpResponse<String> p1 = get(PREFIX + "/page?current=1&size=5");
        assertEquals(200, p1.statusCode());
        HttpResponse<String> p3 = get(PREFIX + "/page?current=3&size=5");
        assertEquals(200, p3.statusCode());
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void pageQuery_withOrderBys_shouldReturn200() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/page?orderBys=price_DESC&current=1&size=10");
        assertEquals(200, r.statusCode());
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void listQuery_shouldReturn200() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/list");
        assertEquals(200, r.statusCode());
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void listQuery_byStatus_shouldReturn200() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/list?status=ON_SALE");
        assertEquals(200, r.statusCode());
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void countQuery_shouldReturn200() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/count");
        assertEquals(200, r.statusCode());
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void countQuery_byStatus_shouldReturn200() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/count?status=DRAFT");
        assertEquals(200, r.statusCode());
    }

    // =================== 综合 ====================

    /**
     * \u9a8c\u8bc1<code>upd.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>del.statusCode()</code> \u7b49\u4e8e <code>200</code>\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void fullCrudLifecycle() throws Exception {
        HttpResponse<String> create = postJson(PREFIX,
                "{\"code\":\"SKU-LC1\",\"name\":\"Life\",\"price\":99.00,\"stock\":7}");
        String id = extractId(create.body());

        HttpResponse<String> upd = putJson(PREFIX + "/" + id,
                "{\"name\":\"LifeCycle\",\"price\":199.00}");
        assertEquals(200, upd.statusCode());

        HttpResponse<String> st = put(PREFIX + "/" + id + "/status?status=ON_SALE");
        assertEquals(200, st.statusCode());

        HttpResponse<String> gid = get(PREFIX + "/" + id);
        assertEquals(200, gid.statusCode());
        assertTrue(gid.body().contains("ON_SALE"));

        HttpResponse<String> del = delete(PREFIX + "/" + id);
        assertEquals(200, del.statusCode());
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>r.body().contains("DELETED")</code>\u4e3a\u771f\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void deleteGoods_getByIdReturnsDeletedStatus() throws Exception {
        HttpResponse<String> create = postJson(PREFIX,
                "{\"code\":\"SKU-SOFT1\",\"name\":\"x\",\"price\":1.00,\"stock\":1}");
        String id = extractId(create.body());
        delete(PREFIX + "/" + id);
        HttpResponse<String> r = get(PREFIX + "/" + id);
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("DELETED"));
    }

    /**
     * \u9a8c\u8bc1<code>r.statusCode() &gt;= 400</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void pageQuery_invalidStatusEnum_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/page?status=BAD&current=1&size=10");
        assertTrue(r.statusCode() >= 400);
    }
}
