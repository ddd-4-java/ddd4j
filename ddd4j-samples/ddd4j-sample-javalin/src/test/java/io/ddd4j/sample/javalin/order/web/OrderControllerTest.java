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

import java.util.NoSuchElementException;
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
import io.ddd4j.sample.order.testkit.http.HttpClient;
import io.ddd4j.sample.order.testkit.http.HttpRequest;
import io.ddd4j.sample.order.testkit.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/** \u901a\u8fc7 <code>client.send()</code>\u3001<code>HttpRequest.newBuilder()</code>\u3001<code>URI.create()</code> \u9a8c\u8bc1 <code>OrderController</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
@Testcontainers
class OrderControllerTest {
    /** \u4e3a\u5f53\u524d\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u72ec\u7acb MySQL \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");
    /** \u8d1f\u8d23\u8f6c\u6362\u6d4b\u8bd5\u6216\u534f\u8bae\u6b63\u6587\u4e2d JSON \u6570\u636e\u7684 Jackson \u6620\u5c04\u5668\u3002 */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** \u5f53\u524d\u7528\u4f8b\u914d\u7f6e\u5e76\u542f\u52a8\u7684\u5e94\u7528\u5b9e\u4f8b\u3002 */
    private static JavalinSample.JavalinApplication application;
    /** \u53d1\u9001\u4e0e\u63a5\u6536\u6d4b\u8bd5\u8bf7\u6c42\u7684\u5ba2\u6237\u7aef\u3002 */
    private static HttpClient client;
    /** \u5f53\u524d\u670d\u52a1\u7684 HTTP \u57fa\u7840\u5730\u5740\u3002 */
    private static String baseUrl;

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>JavalinSample.start()</code>\u3001<code>JavalinEntryContractTest.catalog()</code>\u3001<code>HttpClient.newBuilder()</code>\u3001<code>Duration.ofSeconds()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeAll
    static void start() {
        application = JavalinSample.start(0, JavalinEntryContractTest.catalog(MYSQL), true);
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        baseUrl = "http://localhost:" + application.app().port();
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>application.close()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterAll
    static void stop() {
        if (Objects.nonNull(application)) {
            application.close();
        }
    }

    /**
     * \u901a\u8fc7 <code>client.send()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>client.send(request, HttpResponse.BodyHandlers.ofString())</code>
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    private static HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header(WebHeaders.AUTHORIZATION, "Bearer " + application.token()).header("X-Tenant-Id", "bearer")
                .GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * \u901a\u8fc7 <code>client.send()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @param idempotencyKey \u4f20\u7ed9 <code>Objects.nonNull()</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString())</code>
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
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
     * \u9a8c\u8bc1<code>response.statusCode()</code> \u7b49\u4e8e <code>401</code>\u3002
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    @Test
    void protectedRouteRequiresBearerToken() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/orders/DENIED"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
    }

    /**
     * \u9a8c\u8bc1<code>post("/api/goods/BOOK", JavalinEntryContractTest.GOODS, null).statusCode()</code> \u7b49\u4e8e <code>201</code>\uff0c<code>created.statusCode()</code> \u7b49\u4e8e <code>201</code>\uff0c<code>JSON.readTree(repeated.body())</code> \u7b49\u4e8e <code>JSON.readTree(first.body())</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    @Test
    void createQueryAndPaymentUseSharedTradeAggregate() throws Exception {
        assertEquals(201, post("/api/goods/BOOK", JavalinEntryContractTest.GOODS, null).statusCode());
        String path = "/api/orders/J-001";
        HttpResponse<java.lang.String> created = post(path, JavalinEntryContractTest.ORDER, null);
        assertEquals(201, created.statusCode(), created.body());
        assertEquals(3980, JSON.readTree(created.body()).path("grossAmountCent").asLong());
        assertEquals(200, get(path).statusCode());
        assertEquals(400, post(path + "/payment", "{\"paidAmountCent\":3980}", null).statusCode());
        assertEquals(400, post(path + "/payment", "{\"paidAmountCent\":3980}", "   ").statusCode());
        assertEquals("AWAITING_PAYMENT", JSON.readTree(get(path).body()).path("orderState").asText());
        assertEquals(400, post(path + "/payment", "{}", "confirm-order").statusCode());
        HttpResponse<java.lang.String> first = post(path + "/payment", "{\"paidAmountCent\":3980}", "confirm-order");
        HttpResponse<java.lang.String> repeated = post(path + "/payment", "{\"paidAmountCent\":3980}", "confirm-order");
        assertEquals(200, first.statusCode(), first.body());
        assertEquals(200, repeated.statusCode(), repeated.body());
        assertEquals("CONFIRMED", JSON.readTree(first.body()).path("orderState").asText());
        assertEquals(JSON.readTree(first.body()), JSON.readTree(repeated.body()));
        assertEquals(409, post(path + "/payment", "{\"paidAmountCent\":3979}", "confirm-order").statusCode());
        assertEquals(3980, JSON.readTree(get(path).body()).path("paidAmountCent").asLong());
    }

    /**
     * \u9a8c\u8bc1<code>StrKit.isNotBlank(firstRequestId)</code>\u4e3a\u771f\uff0c<code>StrKit.isNotBlank(secondRequestId)</code>\u4e3a\u771f\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @Test
    void requestIdIsReturnedAndContextDoesNotLeak() throws Exception {
        HttpResponse<String> first = get("/api/orders/MISSING");
        HttpResponse<String> second = get("/api/orders/MISSING");
        String firstRequestId = first.headers().firstValue(WebHeaders.REQUEST_ID).orElseThrow(NoSuchElementException::new);
        String secondRequestId = second.headers().firstValue(WebHeaders.REQUEST_ID).orElseThrow(NoSuchElementException::new);
        assertTrue(StrKit.isNotBlank(firstRequestId));
        assertTrue(StrKit.isNotBlank(secondRequestId));
        assertNotEquals(firstRequestId, secondRequestId);
    }
}
