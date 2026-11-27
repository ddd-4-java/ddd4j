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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
     * @return 当前测试使用的结果，取自 <code>httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).DELETE().build(), HttpResponse.BodyHandlers.ofString())</code>
     * @throws Exception 发起测试请求时失败
     */
    private HttpResponse<String> delete(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).DELETE().build(),
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

    // =================== 1) create ====================

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>201</code>，<code>r.body().contains("SKU-1")</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void create_shouldReturn201() throws Exception {
        HttpResponse<String> r = postJson(PREFIX, "{\"code\":\"SKU-1\",\"name\":\"Phone\",\"price\":999.00,\"stock\":10}");
        assertEquals(201, r.statusCode());
        assertTrue(r.body().contains("SKU-1"));
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>201</code>。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void create_zeroStock_shouldReturn201() throws Exception {
        HttpResponse<String> r = postJson(PREFIX, "{\"code\":\"SKU-2\",\"name\":\"Zero\",\"price\":1.00,\"stock\":0}");
        assertEquals(201, r.statusCode());
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
                    "{\"code\":\"SKU-M" + i + "\",\"name\":\"M\",\"price\":1.00,\"stock\":1}");
            assertEquals(201, r.statusCode());
        }
    }

    // =================== 2) update ====================

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("updated")</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
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
     * 验证<code>r.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void update_unknownId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = putJson(PREFIX + "/999999999",
                "{\"name\":\"x\",\"price\":1.00}");
        assertTrue(r.statusCode() >= 400);
    }

    /**
     * 验证<code>r.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
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
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("ON_SALE")</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
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
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("OFF_SALE")</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
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
     * 验证<code>r.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
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
     * 验证<code>r.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void changeStatus_unknownId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = put(PREFIX + "/999999999/status?status=ON_SALE");
        assertTrue(r.statusCode() >= 400);
    }

    // =================== 4) delete ====================

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
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
     * 验证<code>r.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void delete_unknownId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = delete(PREFIX + "/999999999");
        assertTrue(r.statusCode() >= 400);
    }

    // =================== 5) getById ====================

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("SKU-G1")</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
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
     * 验证<code>r.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void getById_unknownId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/999999999");
        assertTrue(r.statusCode() >= 400);
    }

    // =================== 6) getByCode ====================

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("SKU-BC1")</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
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
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void pageQuery_defaultPage_shouldReturn200() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/page?current=1&size=10");
        assertEquals(200, r.statusCode());
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("SKU-PS1")</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
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
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("SKU-PR1")</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void pageQuery_byPriceRange_shouldReturnMatchingGoods() throws Exception {
        postJson(PREFIX, "{\"code\":\"SKU-PR1\",\"name\":\"x\",\"price\":50.00,\"stock\":1}");
        HttpResponse<String> r = get(PREFIX + "/page?priceMin=10&priceMax=100&current=1&size=10");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("SKU-PR1"));
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("SKU-NL1")</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void pageQuery_byNameLike_shouldReturnMatchingGoods() throws Exception {
        postJson(PREFIX, "{\"code\":\"SKU-NL1\",\"name\":\"iPhoneX\",\"price\":1.00,\"stock\":1}");
        HttpResponse<String> r = get(PREFIX + "/page?nameLike=iPhone&current=1&size=10");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("SKU-NL1"));
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("SKU-EX1")</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void pageQuery_byCode_shouldReturnMatchingGoods() throws Exception {
        postJson(PREFIX, "{\"code\":\"SKU-EX1\",\"name\":\"x\",\"price\":1.00,\"stock\":1}");
        HttpResponse<String> r = get(PREFIX + "/page?code=SKU-EX1&current=1&size=10");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("SKU-EX1"));
    }

    /**
     * 验证<code>p1.statusCode()</code> 等于 <code>200</code>，<code>p3.statusCode()</code> 等于 <code>200</code>。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
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

    // =================== 综合 ====================

    /**
     * 验证<code>upd.statusCode()</code> 等于 <code>200</code>，<code>del.statusCode()</code> 等于 <code>200</code>。
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

        HttpResponse<String> del = delete(PREFIX + "/" + id);
        assertEquals(200, del.statusCode());
    }

    /**
     * 验证<code>r.statusCode()</code> 等于 <code>200</code>，<code>r.body().contains("DELETED")</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
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
     * 验证<code>r.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void pageQuery_invalidStatusEnum_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/page?status=BAD&current=1&size=10");
        assertTrue(r.statusCode() >= 400);
    }
}
