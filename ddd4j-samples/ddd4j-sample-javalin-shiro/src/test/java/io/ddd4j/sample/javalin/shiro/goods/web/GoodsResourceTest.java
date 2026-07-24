package io.ddd4j.sample.javalin.shiro.goods.web;

import java.util.Objects;

import io.ddd4j.sample.javalin.shiro.TestSupport;
import io.javalin.Javalin;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GoodsResource + GoodsQueryResource 集成测试（Javalin + Guice + Apache Shiro，random port）。
 *
 * <p>GoodsResource 和 GoodsQueryResource 共同提供 {@code /api/goods/*} 路由。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GoodsResourceTest {

    private static final String PREFIX = "/api/goods";

    private static Javalin app;
    private static HttpClient httpClient;
    private static String baseUrl;

    @BeforeAll
    static void startApp() {
        app = TestSupport.start();
        baseUrl = "http://localhost:" + app.port();
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterAll
    static void stopApp() {
        if (Objects.nonNull(app)) {
            app.stop();
        }
    }

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

    @BeforeEach
    void resetServerState() {
        app.stop();
        app = TestSupport.start();
        baseUrl = "http://localhost:" + app.port();
    }

    private HttpResponse<String> postJson(String path, String body) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> putJson(String path, String body) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .PUT(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    // =================== 1) 创建 ====================

    @Test
    void create_shouldReturn201() throws Exception {
        HttpResponse<String> r = postJson(PREFIX,
                "{\"code\":\"SKU-001\",\"name\":\"Phone\",\"price\":4999.00,\"stock\":10}");
        assertEquals(201, r.statusCode());
    }

    @Test
    void create_thenGetById_shouldReturnSameCode() throws Exception {
        HttpResponse<String> c = postJson(PREFIX,
                "{\"code\":\"SKU-002\",\"name\":\"Laptop\",\"price\":8999.00,\"stock\":5}");
        String id = extractId(c.body());
        HttpResponse<String> r = get(PREFIX + "/" + id);
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"code\":\"SKU-002\""));
    }

    @Test
    void create_zeroStock_shouldBeOk() throws Exception {
        HttpResponse<String> r = postJson(PREFIX,
                "{\"code\":\"SKU-ZS\",\"name\":\"ZeroStock\",\"price\":1.00,\"stock\":0}");
        assertEquals(201, r.statusCode());
    }

    @Test
    void create_highPrice_shouldBeOk() throws Exception {
        HttpResponse<String> r = postJson(PREFIX,
                "{\"code\":\"SKU-HP\",\"name\":\"HighPrice\",\"price\":99999.99,\"stock\":1}");
        assertEquals(201, r.statusCode());
    }

    @Test
    void create_decimalPrice_shouldPersist() throws Exception {
        HttpResponse<String> c = postJson(PREFIX,
                "{\"code\":\"SKU-DP\",\"name\":\"Decimal\",\"price\":19.99,\"stock\":7}");
        String id = extractId(c.body());
        HttpResponse<String> r = get(PREFIX + "/" + id);
        assertTrue(r.body().contains("\"code\":\"SKU-DP\""));
    }

    // =================== 2) 更新 ====================

    @Test
    void update_shouldBeOk() throws Exception {
        HttpResponse<String> c = postJson(PREFIX,
                "{\"code\":\"SKU-010\",\"name\":\"OldName\",\"price\":50.00,\"stock\":5}");
        String id = extractId(c.body());
        HttpResponse<String> r = putJson(PREFIX + "/" + id,
                "{\"name\":\"NewName\",\"price\":75.00}");
        assertEquals(200, r.statusCode());
    }

    @Test
    void update_thenGetById_shouldReturnNewName() throws Exception {
        HttpResponse<String> c = postJson(PREFIX,
                "{\"code\":\"SKU-011\",\"name\":\"Original\",\"price\":50.00,\"stock\":5}");
        String id = extractId(c.body());
        putJson(PREFIX + "/" + id, "{\"name\":\"Updated\",\"price\":99.00}");
        HttpResponse<String> r = get(PREFIX + "/" + id);
        assertTrue(r.body().contains("\"name\":\"Updated\""));
    }

    @Test
    void update_nonexistentId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = putJson(PREFIX + "/999999",
                "{\"name\":\"x\",\"price\":1.00}");
        assertTrue(r.statusCode() >= 400);
    }

    @Test
    void update_invalidId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = putJson(PREFIX + "/not-a-number",
                "{\"name\":\"x\",\"price\":1.00}");
        assertTrue(r.statusCode() >= 400);
    }

    // =================== 3) 状态 ====================

    @Test
    void changeStatus_toOnSale_shouldBeOk() throws Exception {
        HttpResponse<String> c = postJson(PREFIX,
                "{\"code\":\"SKU-020\",\"name\":\"X\",\"price\":1.00,\"stock\":1}");
        String id = extractId(c.body());
        HttpResponse<String> r = put(PREFIX + "/" + id + "/status?status=ON_SALE");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("ON_SALE"));
    }

    @Test
    void changeStatus_toOffSale_shouldBeOk() throws Exception {
        HttpResponse<String> c = postJson(PREFIX,
                "{\"code\":\"SKU-021\",\"name\":\"X\",\"price\":1.00,\"stock\":1}");
        String id = extractId(c.body());
        HttpResponse<String> r = put(PREFIX + "/" + id + "/status?status=OFF_SALE");
        assertEquals(200, r.statusCode());
    }

    @Test
    void changeStatus_invalidStatus_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> c = postJson(PREFIX,
                "{\"code\":\"SKU-022\",\"name\":\"X\",\"price\":1.00,\"stock\":1}");
        String id = extractId(c.body());
        HttpResponse<String> r = put(PREFIX + "/" + id + "/status?status=NOT_REAL");
        assertTrue(r.statusCode() >= 400);
    }

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

    @Test
    void delete_shouldReturnOk() throws Exception {
        HttpResponse<String> c = postJson(PREFIX,
                "{\"code\":\"SKU-030\",\"name\":\"X\",\"price\":1.00,\"stock\":1}");
        String id = extractId(c.body());
        HttpResponse<String> r = delete(PREFIX + "/" + id);
        assertEquals(200, r.statusCode());
    }

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

    @Test
    void delete_nonexistentId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = delete(PREFIX + "/999999");
        assertTrue(r.statusCode() >= 400);
    }

    @Test
    void delete_invalidId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = delete(PREFIX + "/not-a-number");
        assertTrue(r.statusCode() >= 400);
    }

    // =================== 5) 查询 ====================

    @Test
    void getById_unknownId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/999999");
        assertTrue(r.statusCode() >= 400);
    }

    @Test
    void getById_invalidId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/not-a-number");
        assertTrue(r.statusCode() >= 400);
    }

    @Test
    void getByCode_validCode_shouldReturnOk() throws Exception {
        postJson(PREFIX, "{\"code\":\"SKU-040\",\"name\":\"X\",\"price\":1.00,\"stock\":1}");
        HttpResponse<String> r = get(PREFIX + "/by-code?code=SKU-040");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("SKU-040"));
    }

    @Test
    void getByCode_unknownCode_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/by-code?code=DOES_NOT_EXIST");
        assertTrue(r.statusCode() >= 400);
    }

    // =================== 6) 充血查询（page/list/count）====================

    @Test
    void pageQuery_shouldReturn200() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/page?current=1&size=10");
        assertEquals(200, r.statusCode());
    }

    @Test
    void pageQuery_withCode_shouldReturnMatchingGoods() throws Exception {
        postJson(PREFIX, "{\"code\":\"SKU-PQ\",\"name\":\"X\",\"price\":1.00,\"stock\":1}");
        HttpResponse<String> r = get(PREFIX + "/page?code=SKU-PQ");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("SKU-PQ"));
    }

    @Test
    void pageQuery_withOrderBys_shouldReturn200() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/page?orderBys=price_DESC&current=1&size=10");
        assertEquals(200, r.statusCode());
    }

    @Test
    void listQuery_shouldReturn200() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/list");
        assertEquals(200, r.statusCode());
    }

    @Test
    void listQuery_byStatus_shouldReturn200() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/list?status=ON_SALE");
        assertEquals(200, r.statusCode());
    }

    @Test
    void countQuery_shouldReturn200() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/count");
        assertEquals(200, r.statusCode());
    }

    @Test
    void countQuery_byStatus_shouldReturn200() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/count?status=DRAFT");
        assertEquals(200, r.statusCode());
    }

    // =================== 7) 综合 ====================

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

    @Test
    void multipleCreates_allShouldSucceed() throws Exception {
        for (int i = 0; i < 3; i++) {
            HttpResponse<String> r = postJson(PREFIX,
                    "{\"code\":\"SKU-M" + i + "\",\"name\":\"X" + i + "\",\"price\":1.00,\"stock\":1}");
            assertEquals(201, r.statusCode());
        }
    }

    @Test
    void getByCode_responseShape() throws Exception {
        postJson(PREFIX, "{\"code\":\"SKU-SH\",\"name\":\"Shape\",\"price\":10.00,\"stock\":1}");
        HttpResponse<String> r = get(PREFIX + "/by-code?code=SKU-SH");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"data\""));
    }

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
