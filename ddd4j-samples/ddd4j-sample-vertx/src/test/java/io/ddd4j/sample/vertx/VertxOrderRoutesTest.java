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
import io.ddd4j.vertx.Ddd4jVertxRuntime;
import io.ddd4j.web.core.auth.AuthenticationMode;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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

/**
 * 验证 <code>VertxOrderRoutes</code> 在<code>shouldRejectNullAuthenticationMode</code>等操作中的行为契约。
 */
@Testcontainers
class VertxOrderRoutesTest {
    /**
     * 为当前集成测试提供独立 MySQL 服务的容器。
     */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");
    /**
     * 用于读写 JSON 数据的 Jackson 映射器。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();
    /**
     * 当前 HTTP 框架示例测试调用的交易用例入口。
     */
    private CatalogEndpoints api;
    /**
     * 由当前测试创建和释放的 Vert.x 运行环境。
     */
    private Vertx vertx;
    /**
     * 本用例启动并在结束时关闭的框架运行时。
     */
    private Ddd4jVertxRuntime runtime;
    /**
     * 当前测试启动并负责释放的本地服务。
     */
    private HttpServer server;

    /**
     * 测试执行前执行 <code>Vertx.vertx()</code>、<code>Ddd4jVertxRuntime.create()</code>、<code>runtime.start()</code>、<code>VertxBasicMainTest.catalog()</code> 完成资源准备。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
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
     * 测试结束后执行 <code>await()</code>、<code>server.close()</code>、<code>runtime.close()</code>、<code>vertx.close()</code> 完成资源回收或状态恢复。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
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

    /**
     * 验证<code>VertxOrderRoutes.router(vertx, api, null)</code> 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void shouldRejectNullAuthenticationModeWithStableMessage() {
        assertThatThrownBy(() -> VertxOrderRoutes.router(vertx, api, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("mode must not be null");
    }

    /**
     * 验证<code>request(client, baseUrl, "GET", "/api/orders/VERTX-001", null, null).statusCode()</code> 等于 <code>401</code>，<code>request(client, baseUrl, "POST", "/api/goods/BOOK", token, VertxBasicMainTest.GOODS) .statusCode()</code> 等于 <code>201</code>，<code>objectMapper.readTree(paid.body()).path("orderState").asText()</code> 等于 <code>"CONFIRMED"</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
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
     * 读取 <code>token</code> 供当前测试使用。
     *
     * @param client  用于发出测试请求的客户端
     * @param baseUrl 传给 <code>request()</code> 的 <code>String</code> 输入
     * @return 当前测试使用的结果，取自 <code>token</code>
     * @throws Exception 处理测试数据的序列化时失败
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
     * 通过 <code>client.send()</code> 取得当前测试需要的结果。
     *
     * @param client  用于发出测试请求的客户端
     * @param baseUrl 传给 <code>HttpRequest.newBuilder()</code> 的 <code>String</code> 输入
     * @param method  发给被测服务的 HTTP 方法
     * @param path    测试请求的资源路径或待访问文件路径
     * @param token   从测试登录获得的会话凭据
     * @param body    当前请求或响应中要读取的正文
     * @return 当前测试使用的结果，取自 <code>client.send(builder.build(), HttpResponse.BodyHandlers.ofString())</code>
     * @throws Exception 发起测试请求时失败
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
     * 通过 <code>future.toCompletionStage()</code> 取得当前测试需要的结果。
     *
     * @param <T>    当前契约测试所使用的 Java 类型
     * @param future 传给 <code>future.toCompletionStage()</code> 的 <code>Future&lt;T&gt;</code> 输入
     * @return 当前测试使用的结果，取自 <code>future.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS)</code>
     * @throws Exception 等待并发操作时失败
     */
    private <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
}
