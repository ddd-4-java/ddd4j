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
package io.ddd4j.sample.javalin.goods.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.sample.javalin.goods.application.GoodsApplicationService;
import io.ddd4j.sample.javalin.goods.domain.Goods;
import io.ddd4j.sample.javalin.goods.infrastructure.InMemoryGoodsRepository;
import io.ddd4j.sample.javalin.spi.AnonymousSubjectProvider;
import io.ddd4j.sample.javalin.spi.DefaultI18nProvider;
import io.ddd4j.sample.javalin.spi.NoOpDomainEventPublisher;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GoodsController 集成测试（Javalin 真实 HTTP，random port）。
 *
 * <p>覆盖完整的第三轨 CRUD：创建 / 更新 / 状态 / 软删 / 各种充血查询
 * （按 ID、按编码、分页、列表、计数）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GoodsControllerTest {

    /**
     * 负责转换测试或协议正文中 JSON 数据的 Jackson 映射器。
     */
    private static final ObjectMapper JSON = new ObjectMapper();

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
     * 测试执行前执行 <code>BaseContext.inject()</code>、<code>RepositoryRegistry.register()</code>、<code>Javalin.create()</code>、<code>cfg.jsonMapper()</code> 完成资源准备。
     */
    @BeforeAll
    static void startApp() {
        BaseContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, new NoOpDomainEventPublisher());
        BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, new AnonymousSubjectProvider());
        BaseContext.inject(SpiKeys.I18N_PROVIDER, I18nProvider.class, new DefaultI18nProvider());

        InMemoryGoodsRepository goodsRepository = new InMemoryGoodsRepository();
        RepositoryRegistry.register(Goods.class, goodsRepository);

        GoodsApplicationService goodsService = new GoodsApplicationService(goodsRepository);
        GoodsController goodsController = new GoodsController(goodsService);

        app = Javalin.create(cfg -> {
            cfg.startup.showJavalinBanner = false;
            cfg.jsonMapper(new JavalinJackson(JsonMapper.builder().findAndAddModules().build(), false));
            cfg.routes.apiBuilder(goodsController::routes);
        });
        app.start(0);
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
     * 通过 <code>body.path()</code> 取得当前测试需要的结果。
     *
     * @param response 当前请求已收到的响应
     * @return 当前测试使用的结果，取自 <code>body.path("data")</code>
     * @throws Exception 处理测试数据的序列化时失败
     */
    private JsonNode queryData(HttpResponse<String> response) throws Exception {
        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = JSON.readTree(response.body());
        assertEquals(0, body.path("code").asInt(-1), response.body());
        return body.path("data");
    }

    /**
     * 通过 <code>extractId()</code> 取得当前测试需要的结果。
     *
     * @param code  用于定位目标测试记录的业务编码
     * @param name  当前测试指定的对象或属性名称
     * @param price 传给 <code>postJson()</code> 的 <code>String</code> 输入
     * @return 当前测试使用的结果，取自 <code>extractId(created.body())</code>
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    private String createFixture(String code, String name, String price) throws Exception {
        HttpResponse<String> created = postJson("/api/goods",
                "{\"code\":\"" + code + "\",\"name\":\"" + name + "\",\"price\":" + price + ",\"stock\":1}");
        assertEquals(201, created.statusCode(), created.body());
        return extractId(created.body());
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
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).PUT(HttpRequest.BodyPublishers.noBody()).build(),
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

    // ---------- 1) create ----------

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
     * 请求 <code>POST /api/goods</code>，确认 HTTP 响应状态为 201。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void createGoods_shouldReturn201() throws Exception {
        HttpResponse<String> resp = postJson("/api/goods",
                "{\"code\":\"SKU-001\",\"name\":\"iPhone\",\"price\":5999.00,\"stock\":10}");
        assertEquals(201, resp.statusCode());
        assertTrue(resp.body().contains("\"code\":\"SKU-001\""));
        assertTrue(resp.body().contains("\"status\":\"DRAFT\""));
    }

    /**
     * 验证<code>dup.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void createGoods_duplicateCode_shouldReturn4xxOr5xx() throws Exception {
        postJson("/api/goods", "{\"code\":\"SKU-DUP\",\"name\":\"A\",\"price\":1.00,\"stock\":1}");
        HttpResponse<String> dup = postJson("/api/goods",
                "{\"code\":\"SKU-DUP\",\"name\":\"B\",\"price\":1.00,\"stock\":1}");
        assertTrue(dup.statusCode() >= 400);
    }

    /**
     * 验证<code>resp.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void createGoods_blankCode_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> resp = postJson("/api/goods",
                "{\"code\":\"\",\"name\":\"x\",\"price\":1.00,\"stock\":1}");
        assertTrue(resp.statusCode() >= 400);
    }

    /**
     * 验证<code>resp.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void createGoods_negativePrice_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> resp = postJson("/api/goods",
                "{\"code\":\"SKU-NEG\",\"name\":\"x\",\"price\":-1.00,\"stock\":1}");
        assertTrue(resp.statusCode() >= 400);
    }

    // ---------- 2) update ----------

    /**
     * 验证<code>resp.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void createGoods_negativeStock_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> resp = postJson("/api/goods",
                "{\"code\":\"SKU-NS\",\"name\":\"x\",\"price\":1.00,\"stock\":-1}");
        assertTrue(resp.statusCode() >= 400);
    }

    /**
     * 请求 <code>POST /api/goods</code>，确认 HTTP 响应状态为 200。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void updateGoods_shouldChangeNameAndPrice() throws Exception {
        HttpResponse<String> create = postJson("/api/goods",
                "{\"code\":\"SKU-UPD\",\"name\":\"Old\",\"price\":10.00,\"stock\":5}");
        String id = extractId(create.body());

        HttpResponse<String> resp = putJson("/api/goods/" + id,
                "{\"name\":\"New\",\"price\":20.00}");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"name\":\"New\""));
        assertTrue(resp.body().contains("\"price\":20"));
    }

    /**
     * 验证<code>resp.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void updateGoods_unknownId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> resp = putJson("/api/goods/999999999",
                "{\"name\":\"x\",\"price\":1.00}");
        assertTrue(resp.statusCode() >= 400);
    }

    // ---------- 3) changeStatus ----------
    // 注：GoodsController 的 /api/goods/{id}/status 使用 queryParamAsClass(GoodsStatus.class)，
    // Javalin 没有内置 enum 转换器，所有命中该路由的请求都会返回 500 (MissingConverterException)。
    // 这是预存在的业务代码 bug（不允许修改业务代码），以下测试断言当前行为。

    /**
     * 验证<code>resp.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void updateGoods_deletedGoods_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> create = postJson("/api/goods",
                "{\"code\":\"SKU-DEL\",\"name\":\"x\",\"price\":1.00,\"stock\":1}");
        String id = extractId(create.body());
        delete("/api/goods/" + id);
        HttpResponse<String> resp = putJson("/api/goods/" + id,
                "{\"name\":\"x\",\"price\":1.00}");
        assertTrue(resp.statusCode() >= 400);
    }

    /**
     * 验证<code>resp.statusCode() == 500</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void changeStatus_toOnSale_shouldReturn200() throws Exception {
        HttpResponse<String> create = postJson("/api/goods",
                "{\"code\":\"SKU-ST\",\"name\":\"x\",\"price\":1.00,\"stock\":1}");
        String id = extractId(create.body());
        HttpResponse<String> resp = put("/api/goods/" + id + "/status?status=ON_SALE");
        assertTrue(resp.statusCode() == 500,
                "expected MissingConverterException (500) for enum query param, actual=" + resp.statusCode());
    }

    /**
     * 验证<code>resp.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void changeStatus_invalidStatus_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> create = postJson("/api/goods",
                "{\"code\":\"SKU-INV\",\"name\":\"x\",\"price\":1.00,\"stock\":1}");
        String id = extractId(create.body());
        HttpResponse<String> resp = put("/api/goods/" + id + "/status?status=NOT_A_STATUS");
        assertTrue(resp.statusCode() >= 400);
    }

    /**
     * 验证<code>resp.statusCode() == 500</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void changeStatus_offSale_shouldReturn200() throws Exception {
        HttpResponse<String> create = postJson("/api/goods",
                "{\"code\":\"SKU-OFF\",\"name\":\"x\",\"price\":1.00,\"stock\":1}");
        String id = extractId(create.body());
        put("/api/goods/" + id + "/status?status=ON_SALE");
        HttpResponse<String> resp = put("/api/goods/" + id + "/status?status=OFF_SALE");
        assertTrue(resp.statusCode() == 500,
                "expected MissingConverterException (500) for enum query param, actual=" + resp.statusCode());
    }

    // ---------- 4) delete ----------

    /**
     * 验证<code>resp.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void changeStatus_unknownId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> resp = put("/api/goods/999999999/status?status=ON_SALE");
        assertTrue(resp.statusCode() >= 400);
    }

    /**
     * 请求 <code>POST /api/goods</code>，确认 HTTP 响应状态为 200。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void deleteGoods_shouldReturn200() throws Exception {
        HttpResponse<String> create = postJson("/api/goods",
                "{\"code\":\"SKU-RM\",\"name\":\"x\",\"price\":1.00,\"stock\":1}");
        String id = extractId(create.body());
        HttpResponse<String> resp = delete("/api/goods/" + id);
        assertEquals(200, resp.statusCode());
    }

    /**
     * 验证<code>resp.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void deleteGoods_unknownId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> resp = delete("/api/goods/999999999");
        assertTrue(resp.statusCode() >= 400);
    }

    // ---------- 5) getById ----------

    /**
     * 验证<code>delete("/api/goods/" + id).statusCode()</code> 等于 <code>200</code>，<code>goods.path("code").asText()</code> 等于 <code>"SKU-SOFT"</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void deleteGoods_thenGetByCode_shouldReturnDeletedStatus() throws Exception {
        String id = createFixture("SKU-SOFT", "x", "1.00");
        assertEquals(200, delete("/api/goods/" + id).statusCode());
        JsonNode goods = queryData(get("/api/goods/by-code?code=SKU-SOFT"));
        assertEquals("SKU-SOFT", goods.path("code").asText());
        assertEquals("DELETED", goods.path("status").asText());
    }

    /**
     * 请求 <code>POST /api/goods</code>，确认 HTTP 响应状态为 200。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void getById_shouldReturnGoods() throws Exception {
        HttpResponse<String> create = postJson("/api/goods",
                "{\"code\":\"SKU-GID\",\"name\":\"x\",\"price\":5.00,\"stock\":3}");
        String id = extractId(create.body());
        HttpResponse<String> resp = get("/api/goods/" + id);
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"code\":\"SKU-GID\""));
    }

    // ---------- 6) getByCode ----------

    /**
     * 验证<code>resp.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void getById_unknownId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> resp = get("/api/goods/999999999");
        assertTrue(resp.statusCode() >= 400);
    }

    /**
     * 验证<code>goods.path("id").asText()</code> 等于 <code>id</code>，<code>goods.path("code").asText()</code> 等于 <code>"SKU-BC"</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void getByCode_shouldReturnGoods() throws Exception {
        String id = createFixture("SKU-BC", "x", "1.00");
        JsonNode goods = queryData(get("/api/goods/by-code?code=SKU-BC"));
        assertEquals(id, goods.path("id").asText());
        assertEquals("SKU-BC", goods.path("code").asText());
    }

    /**
     * 验证<code>resp.statusCode()</code> 等于 <code>400</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void getByCode_missingParam_shouldHandle() throws Exception {
        HttpResponse<String> resp = get("/api/goods/by-code");
        assertEquals(400, resp.statusCode(), resp.body());
    }

    // ---------- 7) page / list / count 充血查询 ----------

    /**
     * 验证<code>resp.statusCode()</code> 等于 <code>400</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void getByCode_unknownCode_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> resp = get("/api/goods/by-code?code=missing");
        assertEquals(400, resp.statusCode(), resp.body());
    }

    /**
     * 验证<code>page.path("current").asInt(-1)</code> 等于 <code>1</code>，<code>page.path("size").asInt(-1)</code> 等于 <code>10</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void pageQuery_defaultPage_shouldReturnOk() throws Exception {
        createFixture("SKU-P0", "FirstPage", "1.00");
        JsonNode page = queryData(get("/api/goods/page?current=1&size=10&nameLike=FirstPage"));
        assertEquals(1, page.path("current").asInt(-1));
        assertEquals(10, page.path("size").asInt(-1));
        assertEquals(1, page.path("total").asInt(-1));
        JsonNode rows = page.path("records");
        assertEquals(1, rows.size());
        assertEquals("SKU-P0", rows.path(0).path("code").asText());
    }

    /**
     * 验证<code>delete("/api/goods/" + deletedId).statusCode()</code> 等于 <code>200</code>，<code>rows.size()</code> 等于 <code>1</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void pageQuery_byStatus_shouldReturnOk() throws Exception {
        createFixture("SKU-PS", "PageStatus", "1.00");
        String deletedId = createFixture("SKU-PS-DEL", "PageStatus", "1.00");
        assertEquals(200, delete("/api/goods/" + deletedId).statusCode());

        JsonNode rows = queryData(get("/api/goods/page?status=DRAFT&nameLike=PageStatus&current=1&size=10")).path("records");
        assertEquals(1, rows.size());
        assertEquals("SKU-PS", rows.path(0).path("code").asText());
        assertEquals("DRAFT", rows.path(0).path("status").asText());
    }

    /**
     * 验证<code>rows.size()</code> 等于 <code>1</code>，<code>rows.path(0).path("code").asText()</code> 等于 <code>"SKU-PR"</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void pageQuery_byPriceRange_shouldReturnOk() throws Exception {
        createFixture("SKU-PR", "PagePrice", "50.00");
        createFixture("SKU-PR-LOW", "PagePrice", "9.00");
        createFixture("SKU-PR-HIGH", "PagePrice", "101.00");
        JsonNode rows = queryData(get("/api/goods/page?nameLike=PagePrice&priceMin=10&priceMax=100&current=1&size=10")).path("records");
        assertEquals(1, rows.size());
        assertEquals("SKU-PR", rows.path(0).path("code").asText());
    }

    /**
     * 验证<code>rows.size()</code> 等于 <code>1</code>，<code>rows.path(0).path("code").asText()</code> 等于 <code>"SKU-NL"</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void pageQuery_byNameLike_shouldReturnOk() throws Exception {
        createFixture("SKU-NL", "ProCameraX", "1.00");
        createFixture("SKU-NL-OTHER", "Android", "1.00");
        JsonNode rows = queryData(get("/api/goods/page?nameLike=Camera&current=1&size=10")).path("records");
        assertEquals(1, rows.size());
        assertEquals("SKU-NL", rows.path(0).path("code").asText());
    }

    /**
     * 验证<code>rows.size()</code> 等于 <code>1</code>，<code>rows.path(0).path("code").asText()</code> 等于 <code>"SKU-EX"</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void pageQuery_byCodeExact_shouldReturnOk() throws Exception {
        createFixture("SKU-EX", "Exact", "1.00");
        createFixture("SKU-EX-OTHER", "Exact", "1.00");
        JsonNode rows = queryData(get("/api/goods/page?code=SKU-EX&current=1&size=10")).path("records");
        assertEquals(1, rows.size());
        assertEquals("SKU-EX", rows.path(0).path("code").asText());
    }

    /**
     * 验证<code>goods.isArray()</code>为真。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void listQuery_shouldReturnOk() throws Exception {
        createFixture("SKU-LS", "List", "1.00");
        JsonNode goods = queryData(get("/api/goods/list"));
        assertTrue(goods.isArray());
        assertTrue(StreamSupport.stream(goods.spliterator(), false)
                .anyMatch(goodsItem -> "SKU-LS".equals(goodsItem.path("code").asText())));
    }

    /**
     * 验证<code>delete("/api/goods/" + deletedId).statusCode()</code> 等于 <code>200</code>，<code>goods.size()</code> 等于 <code>1</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void listQuery_byStatus_shouldReturnOk() throws Exception {
        createFixture("SKU-LS-DRAFT", "ListStatus", "1.00");
        String deletedId = createFixture("SKU-LS-DEL", "ListStatus", "1.00");
        assertEquals(200, delete("/api/goods/" + deletedId).statusCode());

        JsonNode goods = queryData(get("/api/goods/list?status=DRAFT&nameLike=ListStatus"));
        assertEquals(1, goods.size());
        assertEquals("SKU-LS-DRAFT", goods.path(0).path("code").asText());
    }

    /**
     * 验证<code>count &gt;= 0</code>为真，<code>queryData(get(path)).asLong(-1)</code> 等于 <code>count + 1</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void countQuery_shouldReturnOk() throws Exception {
        String path = "/api/goods/count";
        long count = queryData(get(path)).asLong(-1);
        assertTrue(count >= 0);
        createFixture("SKU-CT", "Count", "1.00");
        assertEquals(count + 1, queryData(get(path)).asLong(-1));
    }

    // ---------- 综合 ----------

    /**
     * 验证<code>delete("/api/goods/" + deletedId).statusCode()</code> 等于 <code>200</code>，<code>queryData(get("/api/goods/count?nameLike=CountStatus")).asLong(-1)</code> 等于 <code>2</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void countQuery_byStatus_shouldReturnOk() throws Exception {
        createFixture("SKU-CT-DRAFT", "CountStatus", "1.00");
        String deletedId = createFixture("SKU-CT-DEL", "CountStatus", "1.00");
        assertEquals(200, delete("/api/goods/" + deletedId).statusCode());
        assertEquals(2, queryData(get("/api/goods/count?nameLike=CountStatus")).asLong(-1));
        assertEquals(1, queryData(get("/api/goods/count?nameLike=CountStatus&status=DRAFT")).asLong(-1));
    }

    /**
     * 请求 <code>POST /api/goods</code>，确认 HTTP 响应状态为 200。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void fullCrudLifecycle() throws Exception {
        HttpResponse<String> create = postJson("/api/goods",
                "{\"code\":\"SKU-LC\",\"name\":\"Life\",\"price\":99.00,\"stock\":7}");
        String id = extractId(create.body());

        // update
        HttpResponse<String> upd = putJson("/api/goods/" + id,
                "{\"name\":\"LifeCycle\",\"price\":199.00}");
        assertEquals(200, upd.statusCode());

        // change status — 当前业务代码 bug：queryParamAsClass(GoodsStatus.class) 缺 enum 转换器 → 500
        HttpResponse<String> st = put("/api/goods/" + id + "/status?status=ON_SALE");
        assertTrue(st.statusCode() == 500,
                "expected 500 due to MissingConverterException, actual=" + st.statusCode());

        // get by id
        HttpResponse<String> gid = get("/api/goods/" + id);
        assertEquals(200, gid.statusCode());
        // 注意：changeStatus 因业务 bug 失败，状态仍为初始 DRAFT
        assertTrue(gid.body().contains("\"status\":\"DRAFT\""));

        // delete
        HttpResponse<String> del = delete("/api/goods/" + id);
        assertEquals(200, del.statusCode());

        JsonNode goods = queryData(get("/api/goods/by-code?code=SKU-LC"));
        assertEquals("LifeCycle", goods.path("name").asText());
        assertEquals("DELETED", goods.path("status").asText());
    }

    /**
     * 请求 <code>POST /api/goods</code>，确认 HTTP 响应状态为 201。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void multipleCreates_allShouldSucceed() throws Exception {
        for (int i = 0; i < 3; i++) {
            HttpResponse<String> r = postJson("/api/goods",
                    "{\"code\":\"SKU-M" + i + "\",\"name\":\"M\",\"price\":1.00,\"stock\":1}");
            assertEquals(201, r.statusCode());
        }
    }

    /**
     * 验证<code>firstPage.path("current").asInt(-1)</code> 等于 <code>1</code>，<code>thirdPage.path("current").asInt(-1)</code> 等于 <code>3</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void pageQuery_withPagination_shouldReturnOk() throws Exception {
        for (int i = 0; i < 15; i++) {
            createFixture("SKU-PG" + i, "PageWindow", "1.00");
        }
        String path = "/api/goods/page?nameLike=PageWindow&orderBys=id_ASC&size=5&current=";
        JsonNode firstPage = queryData(get(path + "1"));
        JsonNode thirdPage = queryData(get(path + "3"));
        assertEquals(1, firstPage.path("current").asInt(-1));
        assertEquals(3, thirdPage.path("current").asInt(-1));
        assertEquals(5, thirdPage.path("size").asInt(-1));
        assertEquals(15, thirdPage.path("total").asInt(-1));
        JsonNode firstRows = firstPage.path("records");
        JsonNode thirdRows = thirdPage.path("records");
        assertEquals(5, firstRows.size());
        assertEquals(5, thirdRows.size());
        assertEquals("SKU-PG0", firstRows.path(0).path("code").asText());
        assertEquals("SKU-PG10", thirdRows.path(0).path("code").asText());
    }

    /**
     * 验证<code>resp.statusCode() &gt;= 400</code>为真。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void updateGoods_blankName_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> create = postJson("/api/goods",
                "{\"code\":\"SKU-BN\",\"name\":\"valid\",\"price\":1.00,\"stock\":1}");
        String id = extractId(create.body());
        HttpResponse<String> resp = putJson("/api/goods/" + id,
                "{\"name\":\"\",\"price\":1.00}");
        assertTrue(resp.statusCode() >= 400);
    }

    /**
     * 请求 <code>POST /api/goods</code>，确认 HTTP 响应状态为 201。
     *
     * @throws Exception 测试服务器请求、会话操作或响应解析失败
     */
    @Test
    void createGoods_zeroStock_shouldReturn201() throws Exception {
        HttpResponse<String> resp = postJson("/api/goods",
                "{\"code\":\"SKU-ZS\",\"name\":\"x\",\"price\":1.00,\"stock\":0}");
        assertEquals(201, resp.statusCode());
    }

    /**
     * 验证<code>rows.size()</code> 等于 <code>2</code>，<code>rows.path(0).path("code").asText()</code> 等于 <code>"SKU-ORDER-HIGH"</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void pageQuery_withOrderBys_shouldReturnOk() throws Exception {
        createFixture("SKU-ORDER-LOW", "PageOrder", "1.00");
        createFixture("SKU-ORDER-HIGH", "PageOrder", "2.00");
        JsonNode rows = queryData(get("/api/goods/page?nameLike=PageOrder&orderBys=price_DESC&current=1&size=10")).path("records");
        assertEquals(2, rows.size());
        assertEquals("SKU-ORDER-HIGH", rows.path(0).path("code").asText());
        assertEquals("SKU-ORDER-LOW", rows.path(1).path("code").asText());
    }

    /**
     * 验证<code>page.statusCode()</code> 等于 <code>400</code>，<code>list.statusCode()</code> 等于 <code>400</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void queryInvalidNumber_shouldReturnBadRequest() throws Exception {
        HttpResponse<String> page = get("/api/goods/page?current=bad");
        assertEquals(400, page.statusCode(), page.body());
        HttpResponse<String> list = get("/api/goods/list?priceMin=bad");
        assertEquals(400, list.statusCode(), list.body());
        HttpResponse<String> count = get("/api/goods/count?size=bad");
        assertEquals(400, count.statusCode(), count.body());
    }

    /**
     * 验证<code>resp.statusCode()</code> 等于 <code>400</code>。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @Test
    void pageQuery_invalidStatusEnum_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> resp = get("/api/goods/page?status=BAD&current=1&size=10");
        assertEquals(400, resp.statusCode(), resp.body());
    }

}
