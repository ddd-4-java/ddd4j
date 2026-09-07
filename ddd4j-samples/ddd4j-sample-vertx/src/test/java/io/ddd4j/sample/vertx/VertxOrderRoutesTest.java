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
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import io.ddd4j.vertx.Ddd4jVertxRuntime;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class VertxOrderRoutesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Vertx vertx;
    private Ddd4jVertxRuntime runtime;
    private HttpServer server;

    @BeforeEach
    void setUp() throws Exception {
        vertx = Vertx.vertx();
        runtime = Ddd4jVertxRuntime.create(vertx, Collections.emptyList());
        runtime.start();
        InMemoryOrderAdapters adapters = new InMemoryOrderAdapters();
        OrderApplicationService applicationService = new OrderApplicationService(adapters, adapters, adapters,
                adapters, adapters);
        server = await(vertx.createHttpServer()
                .requestHandler(VertxOrderRoutes.router(vertx, applicationService)).listen(0, "127.0.0.1"));
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
    void shouldRunSharedOrderUseCasesThroughVertxHttp() throws Exception {
        String baseUrl = "http://127.0.0.1:" + server.actualPort();
        String token = issueToken(baseUrl);
        HttpResponse<String> create = request(baseUrl, "POST", "/api/orders", token,
                "{\"orderNo\":\"VERTX-001\",\"buyerId\":\"buyer-1\",\"buyerName\":\"Alice\"}");
        assertThat(create.statusCode()).withFailMessage(create.body()).isEqualTo(201);
        String orderId = objectMapper.readTree(create.body()).path("data").path("id").asText();

        HttpResponse<String> line = request(baseUrl, "POST", "/api/orders/" + orderId + "/lines", token,
                "{\"goodsId\":\"goods-1\",\"goodsName\":\"DDD Book\",\"quantity\":2,\"unitPrice\":59.90}");
        assertThat(line.statusCode()).withFailMessage(line.body()).isEqualTo(200);
        assertThat(objectMapper.readTree(line.body()).path("data").path("totalAmount").decimalValue())
                .isEqualByComparingTo("119.80");

        HttpResponse<String> missingKey = request(baseUrl, "POST", "/api/orders/" + orderId + "/pay", token,
                null);
        assertThat(missingKey.statusCode()).withFailMessage(missingKey.body()).isEqualTo(400);

        HttpResponse<String> paid = request(baseUrl, "POST", "/api/orders/" + orderId + "/pay", token,
                null, "vertx-payment-001");
        assertThat(paid.statusCode()).withFailMessage(paid.body()).isEqualTo(200);
        assertThat(objectMapper.readTree(paid.body()).path("data").path("status").asText()).isEqualTo("PAID");
    }

    private String issueToken(String baseUrl) throws Exception {
        HttpResponse<String> response = request(baseUrl, "POST", "/api/auth/tokens/vertx-user", null, null);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
        String token = body.path("data").path("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private HttpResponse<String> request(String baseUrl, String method, String path, String token,
                                         String body) throws Exception {
        return request(baseUrl, method, path, token, body, null);
    }

    private HttpResponse<String> request(String baseUrl, String method, String path, String token,
                                         String body, String idempotencyKey) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        if (Objects.nonNull(token)) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }
        if (Objects.nonNull(idempotencyKey)) {
            connection.setRequestProperty("Idempotency-Key", idempotencyKey);
        }
        if (Objects.nonNull(body)) {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }
        }
        int statusCode = connection.getResponseCode();
        InputStream input = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        StringBuilder responseBody = new StringBuilder();
        if (Objects.nonNull(input)) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while (Objects.nonNull(line = reader.readLine())) {
                    responseBody.append(line);
                }
            }
        }
        return new HttpResponse<>(statusCode, responseBody.toString());
    }

    private <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    private static final class HttpResponse<T> {

        private final int statusCode;
        private final T body;

        private HttpResponse(int statusCode, T body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        private int statusCode() {
            return statusCode;
        }

        private T body() {
            return body;
        }
    }
}
