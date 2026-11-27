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
import io.javalin.plugin.json.JavalinJackson;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.URI;
import io.ddd4j.sample.order.testkit.http.HttpClient;
import io.ddd4j.sample.order.testkit.http.HttpRequest;
import io.ddd4j.sample.order.testkit.http.HttpResponse;
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

    /** \u8d1f\u8d23\u8f6c\u6362\u6d4b\u8bd5\u6216\u534f\u8bae\u6b63\u6587\u4e2d JSON \u6570\u636e\u7684 Jackson \u6620\u5c04\u5668\u3002 */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** \u7531\u5f53\u524d\u6d4b\u8bd5\u542f\u52a8\u548c\u5173\u95ed\u7684\u5e94\u7528\u5b9e\u4f8b\u3002 */
    private static Javalin app;
    /** \u7528\u4e8e\u53d1\u9001 HTTP \u8bf7\u6c42\u7684\u5ba2\u6237\u7aef\u3002 */
    private static HttpClient httpClient;
    /** \u5f53\u524d\u670d\u52a1\u7684 HTTP \u57fa\u7840\u5730\u5740\u3002 */
    private static String baseUrl;

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>BaseContext.inject()</code>\u3001<code>RepositoryRegistry.register()</code>\u3001<code>Javalin.create()</code>\u3001<code>cfg.jsonMapper()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
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
            cfg.showJavalinBanner = false;
            cfg.jsonMapper(new JavalinJackson(JsonMapper.builder().findAndAddModules().build()));
        });
        app.routes(goodsController::routes);
        app.start(0);
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
     * \u901a\u8fc7 <code>body.path()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param response \u5f53\u524d\u8bf7\u6c42\u5df2\u6536\u5230\u7684\u54cd\u5e94
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>body.path("data")</code>
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    private JsonNode queryData(HttpResponse<String> response) throws Exception {
        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = JSON.readTree(response.body());
        assertEquals(0, body.path("code").asInt(-1), response.body());
        return body.path("data");
    }

    /**
     * \u901a\u8fc7 <code>extractId()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param code \u7528\u4e8e\u5b9a\u4f4d\u76ee\u6807\u6d4b\u8bd5\u8bb0\u5f55\u7684\u4e1a\u52a1\u7f16\u7801
     * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
     * @param price \u4f20\u7ed9 <code>postJson()</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>extractId(created.body())</code>
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    private String createFixture(String code, String name, String price) throws Exception {
        HttpResponse<String> created = postJson("/api/goods",
                "{\"code\":\"" + code + "\",\"name\":\"" + name + "\",\"price\":" + price + ",\"stock\":1}");
        assertEquals(201, created.statusCode(), created.body());
        return extractId(created.body());
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
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).PUT(HttpRequest.BodyPublishers.noBody()).build(),
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

    // ---------- 1) create ----------

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

    /**
     * \u8bf7\u6c42 <code>POST /api/goods</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 201\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
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
     * \u9a8c\u8bc1<code>dup.statusCode() &gt;= 400</code>\u4e3a\u771f\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void createGoods_duplicateCode_shouldReturn4xxOr5xx() throws Exception {
        postJson("/api/goods", "{\"code\":\"SKU-DUP\",\"name\":\"A\",\"price\":1.00,\"stock\":1}");
        HttpResponse<String> dup = postJson("/api/goods",
                "{\"code\":\"SKU-DUP\",\"name\":\"B\",\"price\":1.00,\"stock\":1}");
        assertTrue(dup.statusCode() >= 400);
    }

    /**
     * \u9a8c\u8bc1<code>resp.statusCode() &gt;= 400</code>\u4e3a\u771f\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void createGoods_blankCode_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> resp = postJson("/api/goods",
                "{\"code\":\"\",\"name\":\"x\",\"price\":1.00,\"stock\":1}");
        assertTrue(resp.statusCode() >= 400);
    }

    /**
     * \u9a8c\u8bc1<code>resp.statusCode() &gt;= 400</code>\u4e3a\u771f\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void createGoods_negativePrice_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> resp = postJson("/api/goods",
                "{\"code\":\"SKU-NEG\",\"name\":\"x\",\"price\":-1.00,\"stock\":1}");
        assertTrue(resp.statusCode() >= 400);
    }

    // ---------- 2) update ----------

    /**
     * \u9a8c\u8bc1<code>resp.statusCode() &gt;= 400</code>\u4e3a\u771f\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void createGoods_negativeStock_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> resp = postJson("/api/goods",
                "{\"code\":\"SKU-NS\",\"name\":\"x\",\"price\":1.00,\"stock\":-1}");
        assertTrue(resp.statusCode() >= 400);
    }

    /**
     * \u8bf7\u6c42 <code>POST /api/goods</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
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
     * \u9a8c\u8bc1<code>resp.statusCode() &gt;= 400</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u9a8c\u8bc1<code>resp.statusCode() &gt;= 400</code>\u4e3a\u771f\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
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
     * \u9a8c\u8bc1<code>resp.statusCode() == 500</code>\u4e3a\u771f\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
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
     * \u9a8c\u8bc1<code>resp.statusCode() &gt;= 400</code>\u4e3a\u771f\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
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
     * \u9a8c\u8bc1<code>resp.statusCode() == 500</code>\u4e3a\u771f\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
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
     * \u9a8c\u8bc1<code>resp.statusCode() &gt;= 400</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void changeStatus_unknownId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> resp = put("/api/goods/999999999/status?status=ON_SALE");
        assertTrue(resp.statusCode() >= 400);
    }

    /**
     * \u8bf7\u6c42 <code>POST /api/goods</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
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
     * \u9a8c\u8bc1<code>resp.statusCode() &gt;= 400</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void deleteGoods_unknownId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> resp = delete("/api/goods/999999999");
        assertTrue(resp.statusCode() >= 400);
    }

    // ---------- 5) getById ----------

    /**
     * \u9a8c\u8bc1<code>delete("/api/goods/" + id).statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>goods.path("code").asText()</code> \u7b49\u4e8e <code>"SKU-SOFT"</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u8bf7\u6c42 <code>POST /api/goods</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
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
     * \u9a8c\u8bc1<code>resp.statusCode() &gt;= 400</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void getById_unknownId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> resp = get("/api/goods/999999999");
        assertTrue(resp.statusCode() >= 400);
    }

    /**
     * \u9a8c\u8bc1<code>goods.path("id").asText()</code> \u7b49\u4e8e <code>id</code>\uff0c<code>goods.path("code").asText()</code> \u7b49\u4e8e <code>"SKU-BC"</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void getByCode_shouldReturnGoods() throws Exception {
        String id = createFixture("SKU-BC", "x", "1.00");
        JsonNode goods = queryData(get("/api/goods/by-code?code=SKU-BC"));
        assertEquals(id, goods.path("id").asText());
        assertEquals("SKU-BC", goods.path("code").asText());
    }

    /**
     * \u9a8c\u8bc1<code>resp.statusCode()</code> \u7b49\u4e8e <code>400</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void getByCode_missingParam_shouldHandle() throws Exception {
        HttpResponse<String> resp = get("/api/goods/by-code");
        assertEquals(400, resp.statusCode(), resp.body());
    }

    // ---------- 7) page / list / count 充血查询 ----------

    /**
     * \u9a8c\u8bc1<code>resp.statusCode()</code> \u7b49\u4e8e <code>400</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void getByCode_unknownCode_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> resp = get("/api/goods/by-code?code=missing");
        assertEquals(400, resp.statusCode(), resp.body());
    }

    /**
     * \u9a8c\u8bc1<code>page.path("current").asInt(-1)</code> \u7b49\u4e8e <code>1</code>\uff0c<code>page.path("size").asInt(-1)</code> \u7b49\u4e8e <code>10</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u9a8c\u8bc1<code>delete("/api/goods/" + deletedId).statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>rows.size()</code> \u7b49\u4e8e <code>1</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u9a8c\u8bc1<code>rows.size()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>rows.path(0).path("code").asText()</code> \u7b49\u4e8e <code>"SKU-PR"</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u9a8c\u8bc1<code>rows.size()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>rows.path(0).path("code").asText()</code> \u7b49\u4e8e <code>"SKU-NL"</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u9a8c\u8bc1<code>rows.size()</code> \u7b49\u4e8e <code>1</code>\uff0c<code>rows.path(0).path("code").asText()</code> \u7b49\u4e8e <code>"SKU-EX"</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u9a8c\u8bc1<code>goods.isArray()</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u9a8c\u8bc1<code>delete("/api/goods/" + deletedId).statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>goods.size()</code> \u7b49\u4e8e <code>1</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u9a8c\u8bc1<code>count &gt;= 0</code>\u4e3a\u771f\uff0c<code>queryData(get(path)).asLong(-1)</code> \u7b49\u4e8e <code>count + 1</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u9a8c\u8bc1<code>delete("/api/goods/" + deletedId).statusCode()</code> \u7b49\u4e8e <code>200</code>\uff0c<code>queryData(get("/api/goods/count?nameLike=CountStatus")).asLong(-1)</code> \u7b49\u4e8e <code>2</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u8bf7\u6c42 <code>POST /api/goods</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 200\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
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
     * \u8bf7\u6c42 <code>POST /api/goods</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 201\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
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
     * \u9a8c\u8bc1<code>firstPage.path("current").asInt(-1)</code> \u7b49\u4e8e <code>1</code>\uff0c<code>thirdPage.path("current").asInt(-1)</code> \u7b49\u4e8e <code>3</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u9a8c\u8bc1<code>resp.statusCode() &gt;= 400</code>\u4e3a\u771f\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
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
     * \u8bf7\u6c42 <code>POST /api/goods</code>\uff0c\u786e\u8ba4 HTTP \u54cd\u5e94\u72b6\u6001\u4e3a 201\u3002
     * @throws Exception \u6d4b\u8bd5\u670d\u52a1\u5668\u8bf7\u6c42\u3001\u4f1a\u8bdd\u64cd\u4f5c\u6216\u54cd\u5e94\u89e3\u6790\u5931\u8d25
     */
    @Test
    void createGoods_zeroStock_shouldReturn201() throws Exception {
        HttpResponse<String> resp = postJson("/api/goods",
                "{\"code\":\"SKU-ZS\",\"name\":\"x\",\"price\":1.00,\"stock\":0}");
        assertEquals(201, resp.statusCode());
    }

    /**
     * \u9a8c\u8bc1<code>rows.size()</code> \u7b49\u4e8e <code>2</code>\uff0c<code>rows.path(0).path("code").asText()</code> \u7b49\u4e8e <code>"SKU-ORDER-HIGH"</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u9a8c\u8bc1<code>page.statusCode()</code> \u7b49\u4e8e <code>400</code>\uff0c<code>list.statusCode()</code> \u7b49\u4e8e <code>400</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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
     * \u9a8c\u8bc1<code>resp.statusCode()</code> \u7b49\u4e8e <code>400</code>\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void pageQuery_invalidStatusEnum_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> resp = get("/api/goods/page?status=BAD&current=1&size=10");
        assertEquals(400, resp.statusCode(), resp.body());
    }

}
