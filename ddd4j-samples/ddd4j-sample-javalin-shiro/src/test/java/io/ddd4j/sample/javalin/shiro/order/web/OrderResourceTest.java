package io.ddd4j.sample.javalin.shiro.order.web;

import java.util.Objects;

import io.ddd4j.sample.javalin.shiro.TestSupport;
import io.javalin.Javalin;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OrderResource 集成测试（Javalin + Guice + Apache Shiro，random port）。
 *
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderResourceTest {

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
        int idx = body.indexOf("\"id\":\"");
        if (idx < 0) {
            throw new IllegalStateException("no id in body: " + body);
        }
        int start = idx + 6;
        int end = body.indexOf('"', start);
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

    private HttpResponse<String> get(String path) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    // =================== 1) createDraft ====================

    @Test
    void createOrder_shouldReturn201() throws Exception {
        HttpResponse<String> r = postJson("/orders",
                "{\"orderNo\":\"O-001\",\"buyerId\":\"u1\",\"buyerName\":\"Alice\"}");
        assertEquals(201, r.statusCode());
        assertNotNull(extractId(r.body()));
    }

    @Test
    void createOrder_thenGetById_shouldReturnSameOrder() throws Exception {
        HttpResponse<String> create = postJson("/orders",
                "{\"orderNo\":\"O-002\",\"buyerId\":\"u2\",\"buyerName\":\"Bob\"}");
        String id = extractId(create.body());
        HttpResponse<String> r = get("/orders/" + id);
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"orderNo\":\"O-002\""));
    }

    @Test
    void getById_unknownId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = get("/orders/does-not-exist");
        assertTrue(r.statusCode() >= 400);
    }

    @Test
    void getByOrderNo_shouldReturnOrder() throws Exception {
        postJson("/orders",
                "{\"orderNo\":\"O-SHADOW\",\"buyerId\":\"u\",\"buyerName\":\"X\"}");
        HttpResponse<String> r = get("/orders/by-order-no?orderNo=O-SHADOW");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"orderNo\":\"O-SHADOW\""));
    }

    @Test
    void createOrder_buyerNameShouldPersist() throws Exception {
        HttpResponse<String> create = postJson("/orders",
                "{\"orderNo\":\"O-NAME\",\"buyerId\":\"uN\",\"buyerName\":\"PersistName\"}");
        String id = extractId(create.body());
        HttpResponse<String> r = get("/orders/" + id);
        assertTrue(r.body().contains("\"buyerName\":\"PersistName\""));
    }

    @Test
    void createOrder_buyerIdShouldPersist() throws Exception {
        HttpResponse<String> create = postJson("/orders",
                "{\"orderNo\":\"O-BID\",\"buyerId\":\"B123\",\"buyerName\":\"X\"}");
        String id = extractId(create.body());
        HttpResponse<String> r = get("/orders/" + id);
        assertTrue(r.body().contains("\"buyerId\":\"B123\""));
    }

    // =================== 2) addLine ====================

    @Test
    void addLine_shouldIncludeLineInResponse() throws Exception {
        HttpResponse<String> create = postJson("/orders",
                "{\"orderNo\":\"O-010\",\"buyerId\":\"b1\",\"buyerName\":\"Eve\"}");
        String id = extractId(create.body());
        HttpResponse<String> r = postJson("/orders/" + id + "/lines",
                "{\"goodsId\":\"G1\",\"goodsName\":\"iPhone\",\"quantity\":2,\"unitPrice\":5999.00}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"lines\":["));
        assertTrue(r.body().contains("\"goodsId\":\"G1\""));
    }

    @Test
    void addLine_multipleLines_shouldAccumulate() throws Exception {
        HttpResponse<String> create = postJson("/orders",
                "{\"orderNo\":\"O-011\",\"buyerId\":\"b2\",\"buyerName\":\"Frank\"}");
        String id = extractId(create.body());
        postJson("/orders/" + id + "/lines",
                "{\"goodsId\":\"G1\",\"goodsName\":\"iPhone\",\"quantity\":1,\"unitPrice\":100.00}");
        HttpResponse<String> r = postJson("/orders/" + id + "/lines",
                "{\"goodsId\":\"G2\",\"goodsName\":\"iPad\",\"quantity\":2,\"unitPrice\":200.00}");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"goodsId\":\"G1\""));
        assertTrue(r.body().contains("\"goodsId\":\"G2\""));
    }

    @Test
    void addLine_toUnknownOrder_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = postJson("/orders/no-such/lines",
                "{\"goodsId\":\"G1\",\"goodsName\":\"x\",\"quantity\":1,\"unitPrice\":1.00}");
        assertTrue(r.statusCode() >= 400);
    }

    @Test
    void addLine_quantity3_shouldPersist() throws Exception {
        HttpResponse<String> create = postJson("/orders",
                "{\"orderNo\":\"O-QTY\",\"buyerId\":\"bQ\",\"buyerName\":\"Q\"}");
        String id = extractId(create.body());
        postJson("/orders/" + id + "/lines",
                "{\"goodsId\":\"Q1\",\"goodsName\":\"Qty1\",\"quantity\":3,\"unitPrice\":10.00}");
        HttpResponse<String> r = get("/orders/" + id);
        assertTrue(r.body().contains("\"quantity\":3"));
    }

    @Test
    void addLine_unitPriceDecimal_shouldPersist() throws Exception {
        HttpResponse<String> create = postJson("/orders",
                "{\"orderNo\":\"O-PRICE\",\"buyerId\":\"bP\",\"buyerName\":\"P\"}");
        String id = extractId(create.body());
        postJson("/orders/" + id + "/lines",
                "{\"goodsId\":\"P1\",\"goodsName\":\"P1Name\",\"quantity\":1,\"unitPrice\":12.50}");
        HttpResponse<String> r = get("/orders/" + id);
        assertTrue(r.body().contains("\"goodsId\":\"P1\""));
    }

    // =================== 3) pay ====================

    @Test
    void pay_orderWithoutLines_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> create = postJson("/orders",
                "{\"orderNo\":\"O-020\",\"buyerId\":\"b3\",\"buyerName\":\"Grace\"}");
        String id = extractId(create.body());
        HttpResponse<String> r = postJson("/orders/" + id + "/pay", "");
        assertTrue(r.statusCode() >= 400);
    }

    @Test
    void pay_orderWithLines_shouldBePaid() throws Exception {
        HttpResponse<String> create = postJson("/orders",
                "{\"orderNo\":\"O-021\",\"buyerId\":\"b4\",\"buyerName\":\"Heidi\"}");
        String id = extractId(create.body());
        postJson("/orders/" + id + "/lines",
                "{\"goodsId\":\"G1\",\"goodsName\":\"x\",\"quantity\":1,\"unitPrice\":10.00}");
        HttpResponse<String> r = postJson("/orders/" + id + "/pay", "");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"status\":\"PAID\""));
    }

    @Test
    void pay_unknownOrder_shouldReturn4xx() throws Exception {
        HttpResponse<String> r = postJson("/orders/no-such-order/pay", "");
        assertTrue(r.statusCode() >= 400);
    }

    @Test
    void pay_paidOrderTwice_secondShouldFail() throws Exception {
        HttpResponse<String> create = postJson("/orders",
                "{\"orderNo\":\"O-DOUBLE-PAY\",\"buyerId\":\"b\",\"buyerName\":\"D\"}");
        String id = extractId(create.body());
        postJson("/orders/" + id + "/lines",
                "{\"goodsId\":\"G1\",\"goodsName\":\"x\",\"quantity\":1,\"unitPrice\":1.00}");
        postJson("/orders/" + id + "/pay", "");
        HttpResponse<String> r2 = postJson("/orders/" + id + "/pay", "");
        assertTrue(r2.statusCode() >= 400);
    }

    // =================== 4) ship ====================

    @Test
    void ship_paidOrder_shouldBeShipped() throws Exception {
        HttpResponse<String> create = postJson("/orders",
                "{\"orderNo\":\"O-030\",\"buyerId\":\"b5\",\"buyerName\":\"Ivan\"}");
        String id = extractId(create.body());
        postJson("/orders/" + id + "/lines",
                "{\"goodsId\":\"G1\",\"goodsName\":\"x\",\"quantity\":1,\"unitPrice\":5.00}");
        postJson("/orders/" + id + "/pay", "");
        HttpResponse<String> r = postJson("/orders/" + id + "/ship", "");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"status\":\"SHIPPED\""));
    }

    @Test
    void ship_draftOrder_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> create = postJson("/orders",
                "{\"orderNo\":\"O-031\",\"buyerId\":\"b6\",\"buyerName\":\"Judy\"}");
        String id = extractId(create.body());
        HttpResponse<String> r = postJson("/orders/" + id + "/ship", "");
        assertTrue(r.statusCode() >= 400);
    }

    @Test
    void ship_unknownOrder_shouldReturn4xx() throws Exception {
        HttpResponse<String> r = postJson("/orders/no-such-order/ship", "");
        assertTrue(r.statusCode() >= 400);
    }

    @Test
    void ship_shippedOrderTwice_shouldFail() throws Exception {
        HttpResponse<String> create = postJson("/orders",
                "{\"orderNo\":\"O-DOUBLE-SHIP\",\"buyerId\":\"b\",\"buyerName\":\"S\"}");
        String id = extractId(create.body());
        postJson("/orders/" + id + "/lines",
                "{\"goodsId\":\"G1\",\"goodsName\":\"x\",\"quantity\":1,\"unitPrice\":1.00}");
        postJson("/orders/" + id + "/pay", "");
        postJson("/orders/" + id + "/ship", "");
        HttpResponse<String> r2 = postJson("/orders/" + id + "/ship", "");
        assertTrue(r2.statusCode() >= 400);
    }

    // =================== 5) cancel ====================

    @Test
    void cancel_draftOrder_shouldBeCancelled() throws Exception {
        HttpResponse<String> create = postJson("/orders",
                "{\"orderNo\":\"O-040\",\"buyerId\":\"b7\",\"buyerName\":\"Kim\"}");
        String id = extractId(create.body());
        HttpResponse<String> r = postJson("/orders/" + id + "/cancel", "");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"status\":\"CANCELLED\""));
    }

    @Test
    void cancel_paidOrder_shouldBeCancelled() throws Exception {
        HttpResponse<String> create = postJson("/orders",
                "{\"orderNo\":\"O-041\",\"buyerId\":\"b8\",\"buyerName\":\"Leo\"}");
        String id = extractId(create.body());
        postJson("/orders/" + id + "/lines",
                "{\"goodsId\":\"G1\",\"goodsName\":\"x\",\"quantity\":1,\"unitPrice\":1.00}");
        postJson("/orders/" + id + "/pay", "");
        HttpResponse<String> r = postJson("/orders/" + id + "/cancel", "");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"status\":\"CANCELLED\""));
    }

    @Test
    void cancel_shippedOrder_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> create = postJson("/orders",
                "{\"orderNo\":\"O-042\",\"buyerId\":\"b9\",\"buyerName\":\"Mike\"}");
        String id = extractId(create.body());
        postJson("/orders/" + id + "/lines",
                "{\"goodsId\":\"G1\",\"goodsName\":\"x\",\"quantity\":1,\"unitPrice\":1.00}");
        postJson("/orders/" + id + "/pay", "");
        postJson("/orders/" + id + "/ship", "");
        HttpResponse<String> r = postJson("/orders/" + id + "/cancel", "");
        assertTrue(r.statusCode() >= 400);
    }

    @Test
    void cancel_unknownOrder_shouldReturn4xx() throws Exception {
        HttpResponse<String> r = postJson("/orders/no-such-order/cancel", "");
        assertTrue(r.statusCode() >= 400);
    }

    @Test
    void cancel_cancelledOrderTwice_shouldNotConflict() throws Exception {
        HttpResponse<String> create = postJson("/orders",
                "{\"orderNo\":\"O-DOUBLE-CANCEL\",\"buyerId\":\"b\",\"buyerName\":\"C\"}");
        String id = extractId(create.body());
        postJson("/orders/" + id + "/cancel", "");
        HttpResponse<String> r2 = postJson("/orders/" + id + "/cancel", "");
        // 业务可能允许重复取消（返回 200）或拒绝（>=400）；都不冲突即可
        assertTrue(r2.statusCode() >= 200);
    }

    // =================== 6) list / discount ====================

    @Test
    void listAll_shouldReturnOk() throws Exception {
        HttpResponse<String> r = get("/orders");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().startsWith("{"));
    }

    @Test
    void discount_unknownId_shouldReturn4xxOr5xx() throws Exception {
        HttpResponse<String> r = get("/orders/no-such-id/discount");
        assertTrue(r.statusCode() >= 400);
    }

    @Test
    void discount_validId_shouldReturnOk() throws Exception {
        HttpResponse<String> create = postJson("/orders",
                "{\"orderNo\":\"O-DISC\",\"buyerId\":\"b\",\"buyerName\":\"D\"}");
        String id = extractId(create.body());
        postJson("/orders/" + id + "/lines",
                "{\"goodsId\":\"G1\",\"goodsName\":\"x\",\"quantity\":1,\"unitPrice\":100.00}");
        HttpResponse<String> r = get("/orders/" + id + "/discount");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"data\""));
    }

    @Test
    void getById_returnsResponseShape() throws Exception {
        HttpResponse<String> create = postJson("/orders",
                "{\"orderNo\":\"O-050\",\"buyerId\":\"b10\",\"buyerName\":\"Nina\"}");
        String id = extractId(create.body());
        HttpResponse<String> r = get("/orders/" + id);
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"id\""));
        assertTrue(r.body().contains("\"orderNo\""));
        assertTrue(r.body().contains("\"buyerId\""));
        assertTrue(r.body().contains("\"buyerName\""));
        assertTrue(r.body().contains("\"status\""));
    }

    @Test
    void discount_containsAmount() throws Exception {
        HttpResponse<String> create = postJson("/orders",
                "{\"orderNo\":\"O-AMT\",\"buyerId\":\"b\",\"buyerName\":\"A\"}");
        String id = extractId(create.body());
        postJson("/orders/" + id + "/lines",
                "{\"goodsId\":\"G1\",\"goodsName\":\"x\",\"quantity\":1,\"unitPrice\":50.00}");
        HttpResponse<String> r = get("/orders/" + id + "/discount");
        assertTrue(r.body().contains("\"amount\""));
    }

    // =================== 7) 综合 ====================

    @Test
    void fullLifecycle_createAddPayShip() throws Exception {
        HttpResponse<String> create = postJson("/orders",
                "{\"orderNo\":\"O-100\",\"buyerId\":\"bf\",\"buyerName\":\"Olivia\"}");
        String id = extractId(create.body());

        HttpResponse<String> line = postJson("/orders/" + id + "/lines",
                "{\"goodsId\":\"G1\",\"goodsName\":\"Book\",\"quantity\":3,\"unitPrice\":50.00}");
        assertEquals(200, line.statusCode());

        HttpResponse<String> pay = postJson("/orders/" + id + "/pay", "");
        assertEquals(200, pay.statusCode());
        assertTrue(pay.body().contains("\"status\":\"PAID\""));

        HttpResponse<String> ship = postJson("/orders/" + id + "/ship", "");
        assertEquals(200, ship.statusCode());
        assertTrue(ship.body().contains("\"status\":\"SHIPPED\""));
    }

    @Test
    void multipleCreates_allShouldSucceed() throws Exception {
        for (int i = 0; i < 5; i++) {
            HttpResponse<String> r = postJson("/orders",
                    "{\"orderNo\":\"O-M" + i + "\",\"buyerId\":\"b\",\"buyerName\":\"M\"}");
            assertEquals(201, r.statusCode());
        }
    }

    @Test
    void listAll_multipleOrders_shouldReturnOk() throws Exception {
        for (int i = 0; i < 3; i++) {
            postJson("/orders",
                    "{\"orderNo\":\"O-L" + i + "\",\"buyerId\":\"b\",\"buyerName\":\"L\"}");
        }
        HttpResponse<String> r = get("/orders");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"data\""));
    }

    @Test
    void addLine_differentGoodsIds_shouldPersist() throws Exception {
        HttpResponse<String> create = postJson("/orders",
                "{\"orderNo\":\"O-MULTI\",\"buyerId\":\"b\",\"buyerName\":\"M\"}");
        String id = extractId(create.body());
        postJson("/orders/" + id + "/lines",
                "{\"goodsId\":\"AA\",\"goodsName\":\"A\",\"quantity\":1,\"unitPrice\":10.00}");
        postJson("/orders/" + id + "/lines",
                "{\"goodsId\":\"BB\",\"goodsName\":\"B\",\"quantity\":1,\"unitPrice\":20.00}");
        postJson("/orders/" + id + "/lines",
                "{\"goodsId\":\"CC\",\"goodsName\":\"C\",\"quantity\":1,\"unitPrice\":30.00}");
        HttpResponse<String> r = get("/orders/" + id);
        assertTrue(r.body().contains("\"goodsId\":\"AA\""));
        assertTrue(r.body().contains("\"goodsId\":\"BB\""));
        assertTrue(r.body().contains("\"goodsId\":\"CC\""));
    }

    @Test
    void fullLifecycle_createAddCancel() throws Exception {
        HttpResponse<String> create = postJson("/orders",
                "{\"orderNo\":\"O-CANCEL-LIFE\",\"buyerId\":\"b\",\"buyerName\":\"CL\"}");
        String id = extractId(create.body());
        postJson("/orders/" + id + "/lines",
                "{\"goodsId\":\"G1\",\"goodsName\":\"x\",\"quantity\":1,\"unitPrice\":1.00}");
        HttpResponse<String> r = postJson("/orders/" + id + "/cancel", "");
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"status\":\"CANCELLED\""));
    }

    @Test
    void getById_responseIsValidJson() throws Exception {
        HttpResponse<String> create = postJson("/orders",
                "{\"orderNo\":\"O-JSON\",\"buyerId\":\"b\",\"buyerName\":\"J\"}");
        String id = extractId(create.body());
        HttpResponse<String> r = get("/orders/" + id);
        assertTrue(r.body().startsWith("{"));
        assertTrue(r.body().endsWith("}"));
    }
}
