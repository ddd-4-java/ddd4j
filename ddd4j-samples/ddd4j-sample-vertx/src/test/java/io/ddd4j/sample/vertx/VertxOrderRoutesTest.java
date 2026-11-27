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
package io.ddd4j.sample.vertx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.order.adapter.http.CatalogEndpoints;
import io.ddd4j.web.core.auth.AuthenticationMode;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import io.ddd4j.vertx.Ddd4jVertxRuntime;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** \u9a8c\u8bc1 <code>VertxOrderRoutes</code> \u5728<code>shouldRejectNullAuthenticationMode</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
@Testcontainers
class VertxOrderRoutesTest {
    /** \u4e3a\u5f53\u524d\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u72ec\u7acb MySQL \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");
    /** \u5f53\u524d HTTP \u6846\u67b6\u793a\u4f8b\u6d4b\u8bd5\u8c03\u7528\u7684\u4ea4\u6613\u7528\u4f8b\u5165\u53e3\u3002 */
    private CatalogEndpoints api;

    /** \u7528\u4e8e\u8bfb\u5199 JSON \u6570\u636e\u7684 Jackson \u6620\u5c04\u5668\u3002 */
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** \u7531\u5f53\u524d\u6d4b\u8bd5\u521b\u5efa\u548c\u91ca\u653e\u7684 Vert.x \u8fd0\u884c\u73af\u5883\u3002 */
    private Vertx vertx;
    /** \u672c\u7528\u4f8b\u542f\u52a8\u5e76\u5728\u7ed3\u675f\u65f6\u5173\u95ed\u7684\u6846\u67b6\u8fd0\u884c\u65f6\u3002 */
    private Ddd4jVertxRuntime runtime;
    /** \u5f53\u524d\u6d4b\u8bd5\u542f\u52a8\u5e76\u8d1f\u8d23\u91ca\u653e\u7684\u672c\u5730\u670d\u52a1\u3002 */
    private HttpServer server;

    /**
     * \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>Vertx.vertx()</code>\u3001<code>Ddd4jVertxRuntime.create()</code>\u3001<code>runtime.start()</code>\u3001<code>VertxBasicMainTest.catalog()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @BeforeEach
    void setUp() throws Exception {
        vertx = Vertx.vertx();
        runtime = Ddd4jVertxRuntime.create(vertx, List.of());
        runtime.start();
        api = VertxBasicMainTest.catalog(MYSQL);
        server = await(vertx.createHttpServer()
                .requestHandler(VertxOrderRoutes.router(vertx, api, AuthenticationMode.REQUIRED)).listen(0, "127.0.0.1"));
    }

    /**
     * \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>await()</code>\u3001<code>server.close()</code>\u3001<code>runtime.close()</code>\u3001<code>vertx.close()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    @AfterEach
    void tearDown() throws Exception {
        if (Objects.nonNull(server)) {
            await(server.close());
        }
        if (Objects.nonNull(runtime)) {
            runtime.close();
        }
        if (Objects.nonNull(vertx)) {
            await(vertx.close());
        }
    }

    /** \u9a8c\u8bc1<code>VertxOrderRoutes.router(vertx, api, null)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void shouldRejectNullAuthenticationModeWithStableMessage() {
        assertThatThrownBy(() -> VertxOrderRoutes.router(vertx, api, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("mode must not be null");
    }

    /**
     * \u9a8c\u8bc1<code>request(client, baseUrl, "GET", "/api/orders/VERTX-001", null, null).statusCode()</code> \u7b49\u4e8e <code>401</code>\uff0c<code>request(client, baseUrl, "POST", "/api/goods/BOOK", token, VertxBasicMainTest.GOODS) .statusCode()</code> \u7b49\u4e8e <code>201</code>\uff0c<code>objectMapper.readTree(paid.body()).path("orderState").asText()</code> \u7b49\u4e8e <code>"CONFIRMED"</code>\u3002
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    @Test
    void shouldRunSharedOrderUseCasesThroughVertxHttp() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String baseUrl = "http://127.0.0.1:" + server.actualPort();
        String token = issueToken(client, baseUrl);
        assertThat(request(client, baseUrl, "GET", "/api/orders/VERTX-001", null, null).statusCode())
                .isEqualTo(401);
        assertThat(request(client, baseUrl, "POST", "/api/goods/BOOK", token, VertxBasicMainTest.GOODS)
                .statusCode()).isEqualTo(201);
        String orderPath = "/api/orders/VERTX-001";
        var create = request(client, baseUrl, "POST", orderPath, token, VertxBasicMainTest.ORDER);
        assertThat(create.statusCode()).withFailMessage(create.body()).isEqualTo(201);
        assertThat(objectMapper.readTree(create.body()).path("grossAmountCent").asLong()).isEqualTo(1000);
        assertThat(request(client, baseUrl, "POST", orderPath + "/payment", token, "{}").statusCode())
                .isEqualTo(400);
        var paid = request(client, baseUrl, "POST", orderPath + "/payment", token, "{\"paidAmountCent\":1000}");
        assertThat(paid.statusCode()).withFailMessage(paid.body()).isEqualTo(200);
        assertThat(objectMapper.readTree(paid.body()).path("orderState").asText()).isEqualTo("CONFIRMED");
    }

    /**
     * \u8bfb\u53d6 <code>token</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param client \u7528\u4e8e\u53d1\u51fa\u6d4b\u8bd5\u8bf7\u6c42\u7684\u5ba2\u6237\u7aef
     * @param baseUrl \u4f20\u7ed9 <code>request()</code> \u7684 <code>String</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>token</code>
     * @throws Exception \u5904\u7406\u6d4b\u8bd5\u6570\u636e\u7684\u5e8f\u5217\u5316\u65f6\u5931\u8d25
     */
    private String issueToken(HttpClient client, String baseUrl) throws Exception {
        HttpResponse<String> response = request(client, baseUrl, "POST", "/api/auth/tokens/vertx-user", null, null);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
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
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>client.send(builder.build(), HttpResponse.BodyHandlers.ofString())</code>
     * @throws Exception \u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
    private HttpResponse<String> request(HttpClient client, String baseUrl, String method, String path, String token,
                                         String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10)).header("X-Tenant-Id", "route-test");
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

    /**
     * \u901a\u8fc7 <code>future.toCompletionStage()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
     * @param <T> \u5f53\u524d\u5951\u7ea6\u6d4b\u8bd5\u6240\u4f7f\u7528\u7684 Java \u7c7b\u578b
     * @param future \u4f20\u7ed9 <code>future.toCompletionStage()</code> \u7684 <code>Future&lt;T&gt;</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>future.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS)</code>
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    private <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
}
