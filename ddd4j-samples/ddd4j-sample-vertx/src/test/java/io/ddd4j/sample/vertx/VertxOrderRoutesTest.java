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

import com.google.common.collect.ImmutableList;
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
import io.ddd4j.sample.order.testkit.http.HttpClient;
import io.ddd4j.sample.order.testkit.http.HttpRequest;
import io.ddd4j.sample.order.testkit.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class VertxOrderRoutesTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");
    private CatalogEndpoints api;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Vertx vertx;
    private Ddd4jVertxRuntime runtime;
    private HttpServer server;

    @BeforeEach
    void setUp() throws Exception {
        vertx = Vertx.vertx();
        runtime = Ddd4jVertxRuntime.create(vertx, ImmutableList.of());
        runtime.start();
        api = VertxBasicMainTest.catalog(MYSQL);
        server = await(vertx.createHttpServer()
                .requestHandler(VertxOrderRoutes.router(vertx, api, AuthenticationMode.REQUIRED)).listen(0, "127.0.0.1"));
    }

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

    @Test
    void shouldRejectNullAuthenticationModeWithStableMessage() {
        assertThatThrownBy(() -> VertxOrderRoutes.router(vertx, api, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("mode must not be null");
    }

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
        HttpResponse<java.lang.String> create = request(client, baseUrl, "POST", orderPath, token, VertxBasicMainTest.ORDER);
        assertThat(create.statusCode()).withFailMessage(create.body()).isEqualTo(201);
        assertThat(objectMapper.readTree(create.body()).path("grossAmountCent").asLong()).isEqualTo(1000);
        assertThat(request(client, baseUrl, "POST", orderPath + "/payment", token, "{}").statusCode())
                .isEqualTo(400);
        HttpResponse<java.lang.String> paid = request(client, baseUrl, "POST", orderPath + "/payment", token, "{\"paidAmountCent\":1000}");
        assertThat(paid.statusCode()).withFailMessage(paid.body()).isEqualTo(200);
        assertThat(objectMapper.readTree(paid.body()).path("orderState").asText()).isEqualTo("CONFIRMED");
    }

    private String issueToken(HttpClient client, String baseUrl) throws Exception {
        HttpResponse<String> response = request(client, baseUrl, "POST", "/api/auth/tokens/vertx-user", null, null);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
        String token = body.path("data").path("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }

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

    private <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
}
