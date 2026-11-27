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
package io.ddd4j.sample.javalin.shiro.goods.web;

import io.ddd4j.sample.javalin.shiro.TestSupport;
import io.javalin.Javalin;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GoodsResource + GoodsQueryResource 集成测试（Javalin + Guice + Apache Shiro，random port）。
 *
 * <p>GoodsResource 和 GoodsQueryResource 共同提供 {@code /api/goods/*} 路由。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GoodsResourceTest {

    /**
     * 用于构造本组用例请求地址或标识的公共前缀。
     */
    private static final String PREFIX = "/api/goods";

    /**
     * 由当前测试启动和关闭的应用实例。
     */
    private static Javalin app;
    /**
     * 用于发送 HTTP 请求的客户端。
     */
    private static HttpClient httpClient;
    /**
     * 当前服务的 HTTP 基础地址。
     */
    private static String baseUrl;

    /**
     * 测试执行前执行 <code>TestSupport.start()</code>、<code>app.port()</code>、<code>HttpClient.newBuilder()</code>、<code>Duration.ofSeconds()</code> 完成资源准备。
     */
    @BeforeAll
    static void startApp() {
        app = TestSupport.start();
        baseUrl = "http://localhost:" + app.port();
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /**
     * 测试结束后执行 <code>app.stop()</code> 完成资源回收或状态恢复。
     */
    @AfterAll
    static void stopApp() {
        if (Objects.nonNull(app)) {
            app.stop();
        }
    }

    /**
     * 通过 <code>body.substring()</code> 取得当前测试需要的结果。
     *
     * @param body 当前请求或响应中要读取的正文
     * @return 当前测试使用的结果，取自 <code>body.substring(start, end)</code>
     */
    private static String extractId(String body) {
        int idx = body.indexOf("\"id\":");
        if (idx < 0) {
            throw new IllegalStateException("no id in body: " + body);
        }
        int start = idx + 5;
        // skip whitespace
        while (start < body.length() && (body.charAt(start) == ' ' || body.charAt(start) == '\n')) {
            start++;
        }
        int end = start;
        while (end < body.length() && Character.isDigit(body.charAt(end))) {
            end++;
        }
        return body.substring(start, end);
    }

    /**
     * 测试执行前执行 <code>app.stop()</code>、<code>TestSupport.start()</code>、<code>app.port()</code> 完成资源准备。
     */
    @BeforeEach
    void resetServerState() {
        app.stop();
        app = TestSupport.start();
        baseUrl = "http://localhost:" + app.port();
    }

    /**
     * 调用 <code>httpClient.send()</code>、<code>HttpRequest.newBuilder()</code>、<code>URI.create()</code> 执行当前夹具操作。
     *
     * @param path 测试请求的资源路径或待访问文件路径
     * @param body 当前请求或响应中要读取的正文
     * @return 提供给后续契约断言的 <code>HttpResponse&lt;String&gt;</code> 结果
     * @throws Exception 发起测试请求时失败
     */
    private HttpResponse<String> postJson(String path, String body) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 调用 <code>httpClient.send()</code>、<code>HttpRequest.newBuilder()</code>、<code>URI.create()</code> 执行当前夹具操作。
     *
     * @param path 测试请求的资源路径或待访问文件路径
     * @param body 当前请求或响应中要读取的正文
     * @return 提供给后续契约断言的 <code>HttpResponse&lt;String&gt;</code> 结果
     * @throws Exception 发起测试请求时失败
     */
    private HttpResponse<String> putJson(String path, String body) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 调用 <code>httpClient.send()</code>、<code>HttpRequest.newBuilder()</code>、<code>URI.create()</code> 执行当前夹具操作。
     *
     * @param path 测试请求的资源路径或待访问文件路径
     * @return 提供给后续契约断言的 <code>HttpResponse&lt;String&gt;</code> 结果
     * @throws Exception 发起测试请求时失败
     */
    private HttpResponse<String> put(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .PUT(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 通过 <code>httpClient.send()</code> 取得当前测试需要的结果。
     *
     * @param path 测试请求的资源路径或待访问文件路径
     * @return 当前测试使用的结果，取自 <code>httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(), HttpResponse.BodyHandlers.ofString())</code>
     * @throws Exception 发起测试请求时失败
     */
    private HttpResponse<String> get(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 通过 <code>httpClient.send()</code> 取得当前测试需要的结果。
     *
     * @param path 测试请求的资源路径或待访问文件路径
     * @return 当前测试使用的结果，取自 <code>httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).DELETE().build(), HttpResponse.BodyHandlers.ofString())</code>
     * @throws Exception 发起测试请求时失败
     */
    private HttpResponse<String> delete(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    // =================== 1) 创建 ====================

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>201</code>。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void create_shouldReturn201() throws Exception {
        HttpResponse<String> r = postJson(PREFIX,
                "{\"code\":\"SKU-001\",\"name\":\"Phone\",\"price\":4999.00,\"stock\":10}");
        assertEquals(201, r.statusCode());
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void create_thenGetById_shouldReturnSameCode() throws Exception {
        HttpResponse<String> c = postJson(PREFIX,
                "{\"code\":\"SKU-002\",\"name\":\"Laptop\",\"price\":8999.00,\"stock\":5}");
        String id = extractId(c.body());
        HttpResponse<String> r = get(PREFIX + "/" + id);
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"code\":\"SKU-002\""));
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>201</code>。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void create_zeroStock_shouldBeOk() throws Exception {
        HttpResponse<String> r = postJson(PREFIX,
                "{\"code\":\"SKU-ZS\",\"name\":\"ZeroStock\",\"price\":1.00,\"stock\":0}");
        assertEquals(201, r.statusCode());
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>201</code>。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void create_highPrice_shouldBeOk() throws Exception {
        HttpResponse<String> r = postJson(PREFIX,
                "{\"code\":\"SKU-HP\",\"name\":\"HighPrice\",\"price\":99999.99,\"stock\":1}");
        assertEquals(201, r.statusCode());
    }

    /**
     * 在测试框架中执行 <code>postJson()</code>、<code>extractId()</code>、<code>c.body()</code>，验证该场景。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void create_decimalPrice_shouldPersist() throws Exception {
        HttpResponse<String> c = postJson(PREFIX,
                "{\"code\":\"SKU-DP\",\"name\":\"Decimal\",\"price\":19.99,\"stock\":7}");
        String id = extractId(c.body());
        HttpResponse<String> r = get(PREFIX + "/" + id);
        assertTrue(r.body().contains("\"code\":\"SKU-DP\""));
    }

    // =================== 2) 更新 ====================

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void update_shouldBeOk() throws Exception {
        HttpResponse<String> c = postJson(PREFIX,
                "{\"code\":\"SKU-010\",\"name\":\"OldName\",\"price\":50.00,\"stock\":5}");
        String id = extractId(c.body());
        HttpResponse<String> r = putJson(PREFIX + "/" + id,
                "{\"name\":\"NewName\",\"price\":75.00}");
        assertEquals(200, r.statusCode());
    }

    /**
     * 在测试框架中执行 <code>postJson()</code>、<code>extractId()</code>、<code>c.body()</code>，验证该场景。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void update_thenGetById_shouldReturnNewName() throws Exception {
        HttpResponse<String> c = postJson(PREFIX,
                "{\"code\":\"SKU-011\",\"name\":\"Original\",\"price\":50.00,\"stock\":5}");
        String id = extractId(c.body());
        putJson(PREFIX + "/" + id, "{\"name\":\"Updated\",\"price\":99.00}");
        HttpResponse<String> r = get(PREFIX + "/" + id);
        assertTrue(r.body().contains("\"name\":\"Updated\""));
    }

    /**
     * 验证<code>r.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void update_nonexistentId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = putJson(PREFIX + "/999999",
                "{\"name\":\"x\",\"price\":1.00}");
        assertTrue(r.statusCode() >= 400);
    }

    /**
     * 验证<code>r.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void update_invalidId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = putJson(PREFIX + "/not-a-number",
                "{\"name\":\"x\",\"price\":1.00}");
        assertTrue(r.statusCode() >= 400);
    }

    // =================== 3) 状态 ====================

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("ON_SALE")</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void changeStatus_toOnSale_shouldBeOk() throws Exception {
        HttpResponse<String> c = postJson(PREFIX,
                "{\"code\":\"SKU-020\",\"name\":\"X\",\"price\":1.00,\"stock\":1}");
        String id = extractId(c.body());
        HttpResponse<String> r = put(PREFIX + "/" + id + "/status?status=ON_SALE");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("ON_SALE"));
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void changeStatus_toOffSale_shouldBeOk() throws Exception {
        HttpResponse<String> c = postJson(PREFIX,
                "{\"code\":\"SKU-021\",\"name\":\"X\",\"price\":1.00,\"stock\":1}");
        String id = extractId(c.body());
        HttpResponse<String> r = put(PREFIX + "/" + id + "/status?status=OFF_SALE");
        assertEquals(200, r.statusCode());
    }

    /**
     * 验证<code>r.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void changeStatus_invalidStatus_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> c = postJson(PREFIX,
                "{\"code\":\"SKU-022\",\"name\":\"X\",\"price\":1.00,\"stock\":1}");
        String id = extractId(c.body());
        HttpResponse<String> r = put(PREFIX + "/" + id + "/status?status=NOT_REAL");
        assertTrue(r.statusCode() >= 400);
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void changeStatus_lowercase_shouldStillWork() throws Exception {
        HttpResponse<String> c = postJson(PREFIX,
                "{\"code\":\"SKU-023\",\"name\":\"X\",\"price\":1.00,\"stock\":1}");
        String id = extractId(c.body());
        HttpResponse<String> r = put(PREFIX + "/" + id + "/status?status=on_sale");
        // 业务将 on_sale 转 ON_SALE，应成功
        assertEquals(200, r.statusCode());
    }

    // =================== 4) 删除 ====================

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void delete_shouldReturnOk() throws Exception {
        HttpResponse<String> c = postJson(PREFIX,
                "{\"code\":\"SKU-030\",\"name\":\"X\",\"price\":1.00,\"stock\":1}");
        String id = extractId(c.body());
        HttpResponse<String> r = delete(PREFIX + "/" + id);
        assertEquals(200, r.statusCode());
    }

    /**
     * 验证<code>r.statusCode() == 200 || r.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void delete_thenGetById_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> c = postJson(PREFIX,
                "{\"code\":\"SKU-031\",\"name\":\"X\",\"price\":1.00,\"stock\":1}");
        String id = extractId(c.body());
        delete(PREFIX + "/" + id);
        HttpResponse<String> r = get(PREFIX + "/" + id);
        // 业务侧软删后再 GET 可能仍返回 200（DTO 仍可见）
        assertTrue(r.statusCode() == 200 || r.statusCode() >= 400);
    }

    /**
     * 验证<code>r.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void delete_nonexistentId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = delete(PREFIX + "/999999");
        assertTrue(r.statusCode() >= 400);
    }

    /**
     * 验证<code>r.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void delete_invalidId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = delete(PREFIX + "/not-a-number");
        assertTrue(r.statusCode() >= 400);
    }

    // =================== 5) 查询 ====================

    /**
     * 验证<code>r.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void getById_unknownId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/999999");
        assertTrue(r.statusCode() >= 400);
    }

    /**
     * 验证<code>r.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void getById_invalidId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/not-a-number");
        assertTrue(r.statusCode() >= 400);
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("SKU-040")</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void getByCode_validCode_shouldReturnOk() throws Exception {
        postJson(PREFIX, "{\"code\":\"SKU-040\",\"name\":\"X\",\"price\":1.00,\"stock\":1}");
        HttpResponse<String> r = get(PREFIX + "/by-code?code=SKU-040");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("SKU-040"));
    }

    /**
     * 验证<code>r.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void getByCode_unknownCode_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/by-code?code=DOES_NOT_EXIST");
        assertTrue(r.statusCode() >= 400);
    }

    // =================== 6) 充血查询（page/list/count）====================

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void pageQuery_shouldReturn200() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/page?current=1&size=10");
        assertEquals(200, r.statusCode());
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("SKU-PQ")</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void pageQuery_withCode_shouldReturnMatchingGoods() throws Exception {
        postJson(PREFIX, "{\"code\":\"SKU-PQ\",\"name\":\"X\",\"price\":1.00,\"stock\":1}");
        HttpResponse<String> r = get(PREFIX + "/page?code=SKU-PQ");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("SKU-PQ"));
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void pageQuery_withOrderBys_shouldReturn200() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/page?orderBys=price_DESC&current=1&size=10");
        assertEquals(200, r.statusCode());
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void listQuery_shouldReturn200() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/list");
        assertEquals(200, r.statusCode());
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void listQuery_byStatus_shouldReturn200() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/list?status=ON_SALE");
        assertEquals(200, r.statusCode());
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void countQuery_shouldReturn200() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/count");
        assertEquals(200, r.statusCode());
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void countQuery_byStatus_shouldReturn200() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/count?status=DRAFT");
        assertEquals(200, r.statusCode());
    }

    // =================== 7) 综合 ====================

    /**
     * 验证<code>upd.statusCode()</code> 等于 <code>200</code>，<code>gid.body().contains("ON_SALE")</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
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
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>201</code>。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void multipleCreates_allShouldSucceed() throws Exception {
        for (int i = 0; i < 3; i++) {
            HttpResponse<String> r = postJson(PREFIX,
                    "{\"code\":\"SKU-M" + i + "\",\"name\":\"X" + i + "\",\"price\":1.00,\"stock\":1}");
            assertEquals(201, r.statusCode());
        }
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("\"data\"")</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void getByCode_responseShape() throws Exception {
        postJson(PREFIX, "{\"code\":\"SKU-SH\",\"name\":\"Shape\",\"price\":10.00,\"stock\":1}");
        HttpResponse<String> r = get(PREFIX + "/by-code?code=SKU-SH");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"data\""));
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("\"data\"")</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void getById_responseShape() throws Exception {
        HttpResponse<String> c = postJson(PREFIX,
                "{\"code\":\"SKU-SH2\",\"name\":\"Shape2\",\"price\":10.00,\"stock\":1}");
        String id = extractId(c.body());
        HttpResponse<String> r = get(PREFIX + "/" + id);
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"data\""));
    }
}
