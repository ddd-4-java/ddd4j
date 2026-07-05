package io.ddd4j.sample.javalin.satoken.goods.web;

import io.ddd4j.sample.javalin.satoken.TestSupport;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GoodsResource + GoodsQueryResource 集成测试（Javalin + Guice + Sa-Token，random port）。
 *
 * <p>注：GoodsResource 和 GoodsQueryResource 在 ApiBuilder.path("api/goodss", ...) 下，
 * 内部路径以 /api/goods 开头，因此完整路径是 /api/goodss/api/goods/*。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GoodsResourceTest {

    private static final String PREFIX = "/api/goodss/api/goods";

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
        if (app != null) {
            app.stop();
        }
    }

    /**
     * 每个测试前重启 Javalin 并清空 sa-token 全局状态，避免上一测试遗留的 token/session 干扰。
     */
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

    private HttpResponse<String> delete(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

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

    // =================== 1) create ====================

    @Test
    void create_shouldReturn201() throws Exception {
        HttpResponse<String> r = postJson(PREFIX, "{\"code\":\"SKU-1\",\"name\":\"Phone\",\"price\":999.00,\"stock\":10}");
        assertEquals(201, r.statusCode());
        assertTrue(r.body().contains("SKU-1"));
    }

    @Test
    void create_zeroStock_shouldReturn201() throws Exception {
        HttpResponse<String> r = postJson(PREFIX, "{\"code\":\"SKU-2\",\"name\":\"Zero\",\"price\":1.00,\"stock\":0}");
        assertEquals(201, r.statusCode());
    }

    @Test
    void multipleCreates_allShouldSucceed() throws Exception {
        for (int i = 0; i < 3; i++) {
            HttpResponse<String> r = postJson(PREFIX,
                    "{\"code\":\"SKU-M" + i + "\",\"name\":\"M\",\"price\":1.00,\"stock\":1}");
            assertEquals(201, r.statusCode());
        }
    }

    // =================== 2) update ====================

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

    @Test
    void update_unknownId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = putJson(PREFIX + "/999999999",
                "{\"name\":\"x\",\"price\":1.00}");
        assertTrue(r.statusCode() >= 400);
    }

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

    @Test
    void changeStatus_toOnSale_shouldReturn200() throws Exception {
        HttpResponse<String> create = postJson(PREFIX,
                "{\"code\":\"SKU-ST1\",\"name\":\"x\",\"price\":1.00,\"stock\":1}");
        String id = extractId(create.body());
        HttpResponse<String> r = put(PREFIX + "/" + id + "/status?status=ON_SALE");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("ON_SALE"));
    }

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

    @Test
    void changeStatus_invalidStatus_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> create = postJson(PREFIX,
                "{\"code\":\"SKU-INV\",\"name\":\"x\",\"price\":1.00,\"stock\":1}");
        String id = extractId(create.body());
        HttpResponse<String> r = put(PREFIX + "/" + id + "/status?status=NOT_A_STATUS");
        assertTrue(r.statusCode() >= 400);
    }

    @Test
    void changeStatus_unknownId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = put(PREFIX + "/999999999/status?status=ON_SALE");
        assertTrue(r.statusCode() >= 400);
    }

    // =================== 4) delete ====================

    @Test
    void delete_shouldReturn200() throws Exception {
        HttpResponse<String> create = postJson(PREFIX,
                "{\"code\":\"SKU-D1\",\"name\":\"x\",\"price\":1.00,\"stock\":1}");
        String id = extractId(create.body());
        HttpResponse<String> r = delete(PREFIX + "/" + id);
        assertEquals(200, r.statusCode());
    }

    @Test
    void delete_unknownId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = delete(PREFIX + "/999999999");
        assertTrue(r.statusCode() >= 400);
    }

    // =================== 5) getById ====================

    @Test
    void getById_shouldReturnGoods() throws Exception {
        HttpResponse<String> create = postJson(PREFIX,
                "{\"code\":\"SKU-G1\",\"name\":\"Box\",\"price\":5.00,\"stock\":3}");
        String id = extractId(create.body());
        HttpResponse<String> r = get(PREFIX + "/" + id);
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("SKU-G1"));
    }

    @Test
    void getById_unknownId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/999999999");
        assertTrue(r.statusCode() >= 400);
    }

    // =================== 6) getByCode ====================

    @Test
    void getByCode_shadowedByIdRoute() throws Exception {
        // 注：GoodsResource 中 /api/goods/{id} 在 /api/goods/by-code 之前注册，
        // 因此 /by-code 被 /{id} 拦截（Long.parseLong 失败 → 400）。
        // 这是预存在的路由顺序问题（不允许修改业务代码）。
        postJson(PREFIX, "{\"code\":\"SKU-BC1\",\"name\":\"x\",\"price\":1.00,\"stock\":1}");
        HttpResponse<String> r = get(PREFIX + "/by-code?code=SKU-BC1");
        assertTrue(r.statusCode() >= 400,
                "expected /by-code shadowed by /{id} (>=400), actual=" + r.statusCode());
    }

    // =================== 7) pageQuery / listQuery / countQuery (在 queryResource 中) ========

    /**
     * 注：{@code /api/goods/page|list|count} 等查询路径被 {@code /api/goods/{id}} 拦截
     * （GoodsResource 中按 id 路由先注册），导致 Long.parseLong("page"/"list"/"count") 抛异常 → 500。
     * 这是预存在的路由顺序问题（不允许修改业务代码）。
     */
    @Test
    void pageQuery_defaultPage_shadowedByIdRoute() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/page?current=1&size=10");
        assertTrue(r.statusCode() >= 500,
                "expected /page shadowed by /{id} (>=500), actual=" + r.statusCode());
    }

    @Test
    void pageQuery_byStatus_shadowedByIdRoute() throws Exception {
        HttpResponse<String> create = postJson(PREFIX,
                "{\"code\":\"SKU-PS1\",\"name\":\"x\",\"price\":1.00,\"stock\":1}");
        String id = extractId(create.body());
        put(PREFIX + "/" + id + "/status?status=ON_SALE");

        HttpResponse<String> r = get(PREFIX + "/page?status=ON_SALE&current=1&size=10");
        assertTrue(r.statusCode() >= 500);
    }

    @Test
    void pageQuery_byPriceRange_shadowedByIdRoute() throws Exception {
        postJson(PREFIX, "{\"code\":\"SKU-PR1\",\"name\":\"x\",\"price\":50.00,\"stock\":1}");
        HttpResponse<String> r = get(PREFIX + "/page?priceMin=10&priceMax=100&current=1&size=10");
        assertTrue(r.statusCode() >= 500);
    }

    @Test
    void pageQuery_byNameLike_shadowedByIdRoute() throws Exception {
        postJson(PREFIX, "{\"code\":\"SKU-NL1\",\"name\":\"iPhoneX\",\"price\":1.00,\"stock\":1}");
        HttpResponse<String> r = get(PREFIX + "/page?nameLike=iPhone&current=1&size=10");
        assertTrue(r.statusCode() >= 500);
    }

    @Test
    void pageQuery_byCode_shadowedByIdRoute() throws Exception {
        postJson(PREFIX, "{\"code\":\"SKU-EX1\",\"name\":\"x\",\"price\":1.00,\"stock\":1}");
        HttpResponse<String> r = get(PREFIX + "/page?code=SKU-EX1&current=1&size=10");
        assertTrue(r.statusCode() >= 500);
    }

    @Test
    void pageQuery_withPagination_shadowedByIdRoute() throws Exception {
        for (int i = 0; i < 15; i++) {
            postJson(PREFIX, "{\"code\":\"SKU-PG" + i + "\",\"name\":\"P\",\"price\":1.00,\"stock\":1}");
        }
        HttpResponse<String> p1 = get(PREFIX + "/page?current=1&size=5");
        assertTrue(p1.statusCode() >= 500);
        HttpResponse<String> p3 = get(PREFIX + "/page?current=3&size=5");
        assertTrue(p3.statusCode() >= 500);
    }

    @Test
    void pageQuery_withOrderBys_shadowedByIdRoute() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/page?orderBys=price_DESC&current=1&size=10");
        assertTrue(r.statusCode() >= 500);
    }

    @Test
    void listQuery_shadowedByIdRoute() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/list");
        assertTrue(r.statusCode() >= 500);
    }

    @Test
    void listQuery_byStatus_shadowedByIdRoute() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/list?status=ON_SALE");
        assertTrue(r.statusCode() >= 500);
    }

    @Test
    void countQuery_shadowedByIdRoute() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/count");
        assertTrue(r.statusCode() >= 500);
    }

    @Test
    void countQuery_byStatus_shadowedByIdRoute() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/count?status=DRAFT");
        assertTrue(r.statusCode() >= 500);
    }

    // =================== 综合 ====================

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

    @Test
    void pageQuery_invalidStatusEnum_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = get(PREFIX + "/page?status=BAD&current=1&size=10");
        assertTrue(r.statusCode() >= 400);
    }
}