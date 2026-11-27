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

/**
 * 通过 <code>request()</code>、<code>anonymous.getStatus()</code>、<code>issueToken()</code> 验证 <code>HelidonOrderResource</code> 的运行行为。
 */
@Testcontainers
class HelidonOrderResourceTest {
    /**
     * 为当前集成测试提供独立 MySQL 服务的容器。
     */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");
    /**
     * 当前映射、代理或反射调用需要更新的目标对象。
     */
    private static WebTarget target;
    /**
     * 当前测试启动并负责释放的本地服务。
     */
    private static io.helidon.microprofile.server.Server server;
    /**
     * 发送与接收测试请求的客户端。
     */
    private static jakarta.ws.rs.client.Client client;
    /**
     * 用于读写 JSON 数据的 Jackson 映射器。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 根据本次调用更新 <code>server = HelidonOrderApplication.start(0, NativeBasicOrderTest.catalog(MYSQL))</code>、<code>client = jakarta.ws.rs.client.ClientBuilder.newClient()</code>，用于后续测试步骤。
     */
    @org.junit.jupiter.api.BeforeAll
    static void startRequiredProduct() {
        System.setProperty("ddd4j.web.authentication-mode", "REQUIRED");
        server = HelidonOrderApplication.start(0, NativeBasicOrderTest.catalog(MYSQL));
        client = jakarta.ws.rs.client.ClientBuilder.newClient();
        target = client.target("http://127.0.0.1:" + server.port());
    }

    /**
     * 调用 <code>client.close()</code>、<code>server.stop()</code>、<code>System.clearProperty()</code> 执行当前夹具操作。
     */
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
     * 验证<code>anonymous.getStatus()</code> 等于 <code>401</code>，<code>goods.getStatus()</code> 等于 <code>201</code>，<code>objectMapper.readTree(paid.readEntity(String.class)).path("orderState").asText()</code> 等于 <code>"CONFIRMED"</code>。
     *
     * @throws Exception 处理测试数据的序列化时失败
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
     * 读取 <code>token</code> 供当前测试使用。
     *
     * @return 当前测试使用的结果，取自 <code>token</code>
     * @throws Exception 处理测试数据的序列化时失败
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
     * 通过 <code>Objects.isNull()</code> 取得当前测试需要的结果。
     *
     * @param method 发给被测服务的 HTTP 方法
     * @param path   测试请求的资源路径或待访问文件路径
     * @param token  从测试登录获得的会话凭据
     * @param body   当前请求或响应中要读取的正文
     * @return 当前测试使用的结果，取自 <code>Objects.isNull(body) ? builder.method(method) : builder.method(method, Entity.entity(body, MediaType.APPLICATION_JSON_TYPE))</code>
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
     * 通过 <code>response.readEntity()</code> 取得当前测试需要的结果。
     *
     * @param response 当前请求已收到的响应
     * @return 当前测试使用的结果，取自 <code>response.readEntity(String.class)</code>
     */
    private String readAndClose(Response response) {
        try (response) {
            return response.readEntity(String.class);
        }
    }
}
