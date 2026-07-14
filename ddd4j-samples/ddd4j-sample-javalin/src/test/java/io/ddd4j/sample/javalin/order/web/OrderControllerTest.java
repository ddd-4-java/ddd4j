package io.ddd4j.sample.javalin.order.web;

import io.ddd4j.sample.javalin.JavalinSample;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.web.core.WebHeaders;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderControllerTest {

    private static JavalinSample.JavalinApplication application;
    private static HttpClient client;
    private static String baseUrl;

    @BeforeAll
    static void start() {
        application = JavalinSample.start(0);
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        baseUrl = "http://localhost:" + application.app().port();
    }

    @AfterAll
    static void stop() {
        if (Objects.nonNull(application)) {
            application.close();
        }
    }

    @Test
    void protectedRouteRequiresBearerToken() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/orders"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
    }

    @Test
    void createQueryAndLookupUseSharedOrderKernel() throws Exception {
        String orderNo = "J-" + UUID.randomUUID();
        HttpResponse<String> created = post("/api/orders", "{\"orderNo\":\"" + orderNo
                + "\",\"buyerId\":\"buyer-1\",\"buyerName\":\"Alice\"}", null);
        assertEquals(201, created.statusCode());
        String orderId = extract(created.body(), "id");

        HttpResponse<String> byId = get("/api/orders/" + orderId);
        HttpResponse<String> byNo = get("/api/orders/by-no?orderNo=" + orderNo);
        HttpResponse<String> page = get("/api/orders?buyerId=buyer-1&page=1&size=10");

        assertEquals(200, byId.statusCode());
        assertEquals(200, byNo.statusCode());
        assertEquals(200, page.statusCode());
        assertTrue(byNo.body().contains(orderNo));
        assertTrue(page.body().contains(orderNo));
    }

    @Test
    void paymentRequiresIdempotencyKeyAndOnlyTransitionsOnce() throws Exception {
        String orderNo = "J-" + UUID.randomUUID();
        HttpResponse<String> created = post("/api/orders", "{\"orderNo\":\"" + orderNo
                + "\",\"buyerId\":\"buyer-2\",\"buyerName\":\"Bob\"}", null);
        String orderId = extract(created.body(), "id");
        post("/api/orders/" + orderId + "/lines",
                "{\"goodsId\":\"sku-1\",\"goodsName\":\"Book\",\"quantity\":2,\"unitPrice\":19.90}", null);

        assertEquals(400, post("/api/orders/" + orderId + "/pay", "", null).statusCode());
        HttpResponse<String> first = post("/api/orders/" + orderId + "/pay", "", "payment-" + orderId);
        HttpResponse<String> repeated = post("/api/orders/" + orderId + "/pay", "", "payment-" + orderId);

        assertEquals(200, first.statusCode());
        assertEquals(200, repeated.statusCode());
        assertTrue(first.body().contains("\"status\":\"PAID\""));
        assertTrue(repeated.body().contains("\"status\":\"PAID\""));
    }

    @Test
    void requestIdIsReturnedAndContextDoesNotLeak() throws Exception {
        HttpResponse<String> first = get("/api/orders");
        HttpResponse<String> second = get("/api/orders");
        String firstRequestId = first.headers().firstValue(WebHeaders.REQUEST_ID).orElseThrow();
        String secondRequestId = second.headers().firstValue(WebHeaders.REQUEST_ID).orElseThrow();
        assertTrue(StrKit.isNotBlank(firstRequestId));
        assertTrue(StrKit.isNotBlank(secondRequestId));
        assertNotEquals(firstRequestId, secondRequestId);
    }

    private static HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header(WebHeaders.AUTHORIZATION, "Bearer " + application.token())
                .GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String path, String body, String idempotencyKey) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .header(WebHeaders.AUTHORIZATION, "Bearer " + application.token());
        if (Objects.nonNull(idempotencyKey)) {
            request.header(WebHeaders.IDEMPOTENCY_KEY, idempotencyKey);
        }
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String extract(String json, String field) {
        String prefix = "\"" + field + "\":\"";
        int start = json.indexOf(prefix);
        if (start < 0) {
            throw new IllegalArgumentException("field not found: " + field);
        }
        int valueStart = start + prefix.length();
        return json.substring(valueStart, json.indexOf('"', valueStart));
    }
}
