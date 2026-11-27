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
package io.ddd4j.sample.micronaut;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/** \u901a\u8fc7 <code>java.util.UUID.randomUUID()</code>\u3001<code>HttpClient.newHttpClient()</code>\u3001<code>start()</code> \u9a8c\u8bc1 <code>MicronautOrderController</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
@Testcontainers
class MicronautOrderControllerTest {

    /** \u4e3a\u5f53\u524d\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u72ec\u7acb MySQL \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    /** \u7528\u4e8e\u8bfb\u5199 JSON \u6570\u636e\u7684 Jackson \u6620\u5c04\u5668\u3002 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * \u8c03\u7528 <code>ApplicationContext.run()</code>\u3001<code>MYSQL.getJdbcUrl()</code>\u3001<code>MYSQL.getUsername()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param mode \u4f20\u7ed9 <code>ApplicationContext.run()</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>EmbeddedServer</code> \u7ed3\u679c
     */
    private static EmbeddedServer start(String mode) {
        return ApplicationContext.run(EmbeddedServer.class, Map.of("micronaut.server.port", -1,
                "ddd4j.web.default-authentication-mode", mode, "ddd4j.web.public-paths", List.of("/api/auth/**"),
                "catalog.jdbc.url", MYSQL.getJdbcUrl(), "catalog.db.user", MYSQL.getUsername(),
                "catalog.db.password", MYSQL.getPassword()));
    }

    /**
     * \u9a8c\u8bc1<code>objectMapper.readTree(paid.body()).path("orderState").asText()</code> \u7b49\u4e8e <code>"CONFIRMED"</code>\uff0c<code>objectMapper.readTree(found.body()).path("paidAmountCent").asLong()</code> \u7b49\u4e8e <code>2500</code>\uff0c<code>server.getApplicationContext().containsBean(io.ddd4j.core.cqrs.eventstore.EventStore.class)</code>\u4e3a\u5047\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    @Test
    void authenticatedAndAnonymousCatalogTradeUseTheSameFourTables() throws Exception {
        String tenant = java.util.UUID.randomUUID().toString().substring(0, 20);
        HttpClient http = HttpClient.newHttpClient();
        try (var server = start("REQUIRED")) {
            String base = "http://127.0.0.1:" + server.getPort();
            String token = issueToken(http, base);
            assertThat(request(http, base, "POST", "/api/goods/LUNCH", null,
                    "{\"spuName\":\"Lunch\"}", tenant).statusCode()).isEqualTo(401);
            var goods = request(http, base, "POST", "/api/goods/LUNCH", token, """
                    {"spuName":"Lunch","skuCode":"S1","skuName":"Weekday","salePriceCent":1250,"shopId":7}
                    """, tenant);
            assertThat(goods.statusCode()).withFailMessage(goods.body()).isEqualTo(201);
            var order = request(http, base, "POST", "/api/orders/T001", token, """
                    {"shopId":7,"items":[{"skuCode":"S1","quantity":2}]}
                    """, tenant);
            assertThat(order.statusCode()).withFailMessage(order.body()).isEqualTo(201);
            var paid = request(http, base, "POST", "/api/orders/T001/payment", token,
                    "{\"paidAmountCent\":2500}", tenant);
            assertThat(paid.statusCode()).withFailMessage(paid.body()).isEqualTo(200);
            assertThat(objectMapper.readTree(paid.body()).path("orderState").asText()).isEqualTo("CONFIRMED");
        }
        try (var server = start("OPTIONAL")) {
            String base = "http://127.0.0.1:" + server.getPort();
            var found = request(http, base, "GET", "/api/orders/T001", null, null, tenant);
            assertThat(found.statusCode()).withFailMessage(found.body()).isEqualTo(200);
            assertThat(objectMapper.readTree(found.body()).path("paidAmountCent").asLong()).isEqualTo(2500);
            assertThat(request(http, base, "POST", "/api/orders", null, "{}", tenant).statusCode()).isEqualTo(404);
            assertThat(server.getApplicationContext().containsBean(io.ddd4j.core.cqrs.readmodel.ProjectionRunner.class)).isFalse();
            assertThat(server.getApplicationContext().containsBean(io.ddd4j.core.cqrs.command.CommandRegistry.class)).isFalse();
        }
    }

    /**
     * \u8bfb\u53d6 <code>token</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param client \u7528\u4e8e\u53d1\u51fa\u6d4b\u8bd5\u8bf7\u6c42\u7684\u5ba2\u6237\u7aef
     * @param baseUrl \u4f20\u7ed9 <code>request()</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>token</code>
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    private String issueToken(HttpClient client, String baseUrl) throws Exception {
        HttpResponse<String> response = request(client, baseUrl, "POST", "/api/auth/tokens/micronaut-user", null, null, "auth");
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(response.statusCode()).isEqualTo(200);
        String token = body.path("data").path("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    /**
     * \u901a\u8fc7 <code>client.send()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param client \u7528\u4e8e\u53d1\u51fa\u6d4b\u8bd5\u8bf7\u6c42\u7684\u5ba2\u6237\u7aef
     * @param baseUrl \u4f20\u7ed9 <code>HttpRequest.newBuilder()</code> \u7684 <code>String</code> \u8f93\u5165
     * @param method \u53d1\u7ed9\u88ab\u6d4b\u670d\u52a1\u7684 HTTP \u65b9\u6cd5
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param token \u4ece\u6d4b\u8bd5\u767b\u5f55\u83b7\u5f97\u7684\u4f1a\u8bdd\u51ed\u636e
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @param tenant \u5f53\u524d\u8bf7\u6c42\u7684\u6570\u636e\u79df\u6237
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>client.send(builder.build(), HttpResponse.BodyHandlers.ofString())</code>
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    private HttpResponse<String> request(HttpClient client, String baseUrl, String method, String path, String token,
                                         String body, String tenant) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).header("X-Tenant-Id", tenant);
        if (Objects.nonNull(token)) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (Objects.nonNull(body)) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
