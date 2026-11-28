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

/**
 * 通过 <code>java.util.UUID.randomUUID()</code>、<code>HttpClient.newHttpClient()</code>、<code>start()</code> 验证 <code>MicronautOrderController</code> 的运行行为。
 */
@Testcontainers
class MicronautOrderControllerTest {

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
     * 调用 <code>ApplicationContext.run()</code>、<code>MYSQL.getJdbcUrl()</code>、<code>MYSQL.getUsername()</code> 执行当前夹具操作。
     *
     * @param mode 传给 <code>ApplicationContext.run()</code> 的 <code>String</code> 输入
     * @return 提供给后续契约断言的 <code>EmbeddedServer</code> 结果
     */
    private static EmbeddedServer start(String mode) {
        return ApplicationContext.run(EmbeddedServer.class, Map.of("micronaut.server.port", -1,
                "ddd4j.web.default-authentication-mode", mode, "ddd4j.web.public-paths", List.of("/api/auth/**"),
                "catalog.jdbc.url", MYSQL.getJdbcUrl(), "catalog.db.user", MYSQL.getUsername(),
                "catalog.db.password", MYSQL.getPassword()));
    }

    /**
     * 验证<code>objectMapper.readTree(paid.body()).path("orderState").asText()</code> 等于 <code>"CONFIRMED"</code>，<code>objectMapper.readTree(found.body()).path("paidAmountCent").asLong()</code> 等于 <code>2500</code>，<code>server.getApplicationContext().containsBean(io.ddd4j.core.cqrs.eventstore.EventStore.class)</code>为假。
     *
     * @throws Exception 处理测试数据的序列化时失败
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
            assertThat(objectMapper.readTree(order.body()).path("grossAmountCent").asLong()).isEqualTo(2500);
            assertThat(request(http, base, "POST", "/api/orders/T001/payment", token,
                    "{\"paidAmountCent\":2500}", tenant).statusCode()).isEqualTo(400);
            var paid = request(http, base, "POST", "/api/orders/T001/payment", token,
                    "{\"paidAmountCent\":2500}", tenant, "checkout-confirmation");
            assertThat(paid.statusCode()).withFailMessage(paid.body()).isEqualTo(200);
            assertThat(objectMapper.readTree(paid.body()).path("orderState").asText()).isEqualTo("CONFIRMED");
        }
        try (var server = start("OPTIONAL")) {
            String base = "http://127.0.0.1:" + server.getPort();
            var found = request(http, base, "GET", "/api/orders/T001", null, null, tenant);
            HttpResponse<String> direct = request(http, base, "POST", "/api/orders/T002", null,
                    "{\"shopId\":7,\"items\":[{\"skuCode\":\"S1\",\"quantity\":1}]}", tenant);
            assertThat(direct.statusCode()).withFailMessage(direct.body()).isEqualTo(201);
            assertThat(objectMapper.readTree(direct.body()).path("grossAmountCent").asLong()).isEqualTo(1250);
            assertThat(found.statusCode()).withFailMessage(found.body()).isEqualTo(200);
            assertThat(objectMapper.readTree(found.body()).path("paidAmountCent").asLong()).isEqualTo(2500);
            assertThat(request(http, base, "POST", "/api/orders", null, "{}", tenant).statusCode()).isEqualTo(404);
            assertThat(server.getApplicationContext().containsBean(io.ddd4j.core.cqrs.readmodel.ProjectionRunner.class)).isFalse();
            assertThat(server.getApplicationContext().containsBean(io.ddd4j.core.cqrs.eventstore.EventStore.class)).isFalse();
        }
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
        HttpResponse<String> response = request(client, baseUrl, "POST", "/api/auth/tokens/micronaut-user", null, null, "auth");
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(response.statusCode()).isEqualTo(200);
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
     * @param tenant  当前请求的数据租户
     * @return 当前测试使用的结果，取自 <code>client.send(builder.build(), HttpResponse.BodyHandlers.ofString())</code>
     * @throws Exception 发起测试请求时失败
     */
    private HttpResponse<String> request(HttpClient client, String baseUrl, String method, String path, String token,
                                         String body, String tenant) throws Exception {
        return request(client, baseUrl, method, path, token, body, tenant, null);
    }

    /**
     * 发送可选带支付幂等键的请求。
     * @param client 测试 HTTP 客户端
     * @param baseUrl 运行中服务的地址
     * @param method HTTP 方法
     * @param path 资源路径
     * @param token 登录令牌，可为空
     * @param body 请求体，可为空
     * @param tenant 商品与订单的租户
     * @param idempotencyKey 支付幂等键，为空时省略请求头
     * @return 服务返回的原始响应
     * @throws Exception 请求发送或读取失败
     */
    private HttpResponse<String> request(HttpClient client, String baseUrl, String method, String path, String token,
                                         String body, String tenant, String idempotencyKey) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).header("X-Tenant-Id", tenant);
        if (Objects.nonNull(idempotencyKey)) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
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
