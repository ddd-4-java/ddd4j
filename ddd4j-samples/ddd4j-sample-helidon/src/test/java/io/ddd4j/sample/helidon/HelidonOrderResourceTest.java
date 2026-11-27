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
package io.ddd4j.sample.helidon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/** \u901a\u8fc7 <code>request()</code>\u3001<code>anonymous.getStatus()</code>\u3001<code>issueToken()</code> \u9a8c\u8bc1 <code>HelidonOrderResource</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
@Testcontainers
class HelidonOrderResourceTest {
    /** \u4e3a\u5f53\u524d\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u72ec\u7acb MySQL \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");
    /** \u5f53\u524d\u6620\u5c04\u3001\u4ee3\u7406\u6216\u53cd\u5c04\u8c03\u7528\u9700\u8981\u66f4\u65b0\u7684\u76ee\u6807\u5bf9\u8c61\u3002 */
    private static WebTarget target;
    /** \u5f53\u524d\u6d4b\u8bd5\u542f\u52a8\u5e76\u8d1f\u8d23\u91ca\u653e\u7684\u672c\u5730\u670d\u52a1\u3002 */
    private static io.helidon.microprofile.server.Server server;
    /** \u53d1\u9001\u4e0e\u63a5\u6536\u6d4b\u8bd5\u8bf7\u6c42\u7684\u5ba2\u6237\u7aef\u3002 */
    private static jakarta.ws.rs.client.Client client;
    /** \u7528\u4e8e\u8bfb\u5199 JSON \u6570\u636e\u7684 Jackson \u6620\u5c04\u5668\u3002 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>server = HelidonOrderApplication.start(0, NativeBasicOrderTest.catalog(MYSQL))</code>\u3001<code>client = jakarta.ws.rs.client.ClientBuilder.newClient()</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002 */
    @org.junit.jupiter.api.BeforeAll
    static void startRequiredProduct() {
        System.setProperty("ddd4j.web.authentication-mode", "REQUIRED");
        server = HelidonOrderApplication.start(0, NativeBasicOrderTest.catalog(MYSQL));
        client = jakarta.ws.rs.client.ClientBuilder.newClient();
        target = client.target("http://127.0.0.1:" + server.port());
    }

    /** \u8c03\u7528 <code>client.close()</code>\u3001<code>server.stop()</code>\u3001<code>System.clearProperty()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002 */
    @org.junit.jupiter.api.AfterAll
    static void stopRequiredProduct() {
        if (Objects.nonNull(client)) {
            client.close();
        }
        if (Objects.nonNull(server)) {
            server.stop();
        }
        System.clearProperty("ddd4j.web.authentication-mode");
    }

    /**
     * \u9a8c\u8bc1<code>anonymous.getStatus()</code> \u7b49\u4e8e <code>401</code>\uff0c<code>goods.getStatus()</code> \u7b49\u4e8e <code>201</code>\uff0c<code>objectMapper.readTree(paid.readEntity(String.class)).path("orderState").asText()</code> \u7b49\u4e8e <code>"CONFIRMED"</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    @Test
    void shouldRunSharedOrderUseCasesThroughHelidonHttp() throws Exception {
        try (var anonymous = request("GET", "/api/orders/HELIDON-001", null, null)) {
            assertThat(anonymous.getStatus()).isEqualTo(401);
        }
        String token = issueToken();
        try (var goods = request("POST", "/api/goods/BOOK", token, """
                {"spuName":"Book","skuCode":"S1","skuName":"Edition","shopId":7,"salePriceCent":5990}
                """)) {
            assertThat(goods.getStatus()).isEqualTo(201);
        }
        String orderPath = "/api/orders/HELIDON-001";
        try (var order = request("POST", orderPath, token, """
                {"shopId":7,"items":[{"skuCode":"S1","quantity":2}]}
                """)) {
            assertThat(order.getStatus()).isEqualTo(201);
            assertThat(objectMapper.readTree(order.readEntity(String.class)).path("grossAmountCent").asLong())
                    .isEqualTo(11980);
        }
        try (var missing = request("POST", orderPath + "/payment", token, "{}")) {
            assertThat(missing.getStatus()).isEqualTo(400);
        }
        try (var paid = request("POST", orderPath + "/payment", token, "{\"paidAmountCent\":11980}")) {
            assertThat(paid.getStatus()).isEqualTo(200);
            assertThat(objectMapper.readTree(paid.readEntity(String.class)).path("orderState").asText())
                    .isEqualTo("CONFIRMED");
        }
    }

    /**
     * \u8bfb\u53d6 <code>token</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>token</code>
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    private String issueToken() throws Exception {
        Response response = request("POST", "/api/auth/tokens/helidon-user", null, null);
        String body = readAndClose(response);
        assertThat(response.getStatus()).withFailMessage(body).isEqualTo(200);
        JsonNode payload = objectMapper.readTree(body);
        String token = payload.path("data").path("token").asText();
        assertThat(token).withFailMessage(body).isNotBlank();
        return token;
    }

    /**
     * \u901a\u8fc7 <code>Objects.isNull()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>Objects.isNull(body) ? builder.method(method) : builder.method(method, Entity.entity(body, MediaType.APPLICATION_JSON_TYPE))</code>
     */
    private Response request(String method, String path, String token, String body) {
        Invocation.Builder builder = target.path(path).request().header("X-Tenant-Id", "helidon-auth").header("Idempotency-Key", path.endsWith("/payment") ? "checkout-confirmation" : "");
        if (Objects.nonNull(token)) {
            builder.header("Authorization", "Bearer " + token);
        }
        return Objects.isNull(body)
                ? builder.method(method)
                : builder.method(method, Entity.entity(body, MediaType.APPLICATION_JSON_TYPE));
    }

    /**
     * \u901a\u8fc7 <code>response.readEntity()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param response \u5f53\u524d\u8bf7\u6c42\u5df2\u6536\u5230\u7684\u54cd\u5e94
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>response.readEntity(String.class)</code>
     */
    private String readAndClose(Response response) {
        try (response) {
            return response.readEntity(String.class);
        }
    }
}
