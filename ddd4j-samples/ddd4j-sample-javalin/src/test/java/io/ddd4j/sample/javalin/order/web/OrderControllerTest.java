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

@Testcontainers
class OrderControllerTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");
    private static final ObjectMapper JSON = new ObjectMapper();

    private static JavalinSample.JavalinApplication application;
    private static HttpClient client;
    private static String baseUrl;

    @BeforeAll
    static void start() {
        application = JavalinSample.start(0, JavalinEntryContractTest.catalog(MYSQL), true);
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        baseUrl = "http://localhost:" + application.app().port();
    }

    @AfterAll
    static void stop() {
        if (Objects.nonNull(application)) {
            application.close();
        }
    }

    private static HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header(WebHeaders.AUTHORIZATION, "Bearer " + application.token()).header("X-Tenant-Id", "bearer")
                .GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

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

    @Test
    void protectedRouteRequiresBearerToken() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/orders/DENIED"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
    }

    @Test
    void createQueryAndPaymentUseSharedTradeAggregate() throws Exception {
        assertEquals(201, post("/api/goods/BOOK", JavalinEntryContractTest.GOODS, null).statusCode());
        String path = "/api/orders/J-001";
        HttpResponse<java.lang.String> created = post(path, JavalinEntryContractTest.ORDER, null);
        assertEquals(201, created.statusCode(), created.body());
        assertEquals(3980, JSON.readTree(created.body()).path("grossAmountCent").asLong());
        assertEquals(200, get(path).statusCode());
        assertEquals(400, post(path + "/payment", "{}", null).statusCode());
        HttpResponse<java.lang.String> first = post(path + "/payment", "{\"paidAmountCent\":3980}", null);
        HttpResponse<java.lang.String> repeated = post(path + "/payment", "{\"paidAmountCent\":3980}", null);
        assertEquals(200, first.statusCode(), first.body());
        assertEquals(200, repeated.statusCode(), repeated.body());
        assertEquals("CONFIRMED", JSON.readTree(first.body()).path("orderState").asText());
        assertEquals(JSON.readTree(first.body()), JSON.readTree(repeated.body()));
    }

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
