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
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class HelidonOrderResourceTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");
    private static WebTarget target;
    private static io.helidon.microprofile.server.Server server;
    private static javax.ws.rs.client.Client client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.junit.jupiter.api.BeforeAll
    static void startRequiredProduct() {
        System.setProperty("ddd4j.web.authentication-mode", "REQUIRED");
        server = HelidonOrderApplication.start(0, NativeBasicOrderTest.catalog(MYSQL));
        client = javax.ws.rs.client.ClientBuilder.newClient();
        target = client.target("http://127.0.0.1:" + server.port());
    }

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

    @Test
    void shouldRunSharedOrderUseCasesThroughHelidonHttp() throws Exception {
        try (Response anonymous = request("GET", "/api/orders/HELIDON-001", null, null)) {
            assertThat(anonymous.getStatus()).isEqualTo(401);
        }
        String token = issueToken();
        try (Response goods = request("POST", "/api/goods/BOOK", token, "{\"spuName\":\"Book\",\"skuCode\":\"S1\",\"skuName\":\"Edition\",\"shopId\":7,\"salePriceCent\":5990}\n")) {
            assertThat(goods.getStatus()).isEqualTo(201);
        }
        String orderPath = "/api/orders/HELIDON-001";
        try (Response order = request("POST", orderPath, token, "{\"shopId\":7,\"items\":[{\"skuCode\":\"S1\",\"quantity\":2}]}\n")) {
            assertThat(order.getStatus()).isEqualTo(201);
            assertThat(objectMapper.readTree(order.readEntity(String.class)).path("grossAmountCent").asLong())
                    .isEqualTo(11980);
        }
        try (Response missing = request("POST", orderPath + "/payment", token, "{}")) {
            assertThat(missing.getStatus()).isEqualTo(400);
        }
        try (Response paid = request("POST", orderPath + "/payment", token, "{\"paidAmountCent\":11980}")) {
            assertThat(paid.getStatus()).isEqualTo(200);
            assertThat(objectMapper.readTree(paid.readEntity(String.class)).path("orderState").asText())
                    .isEqualTo("CONFIRMED");
        }
    }

    private String issueToken() throws Exception {
        Response response = request("POST", "/api/auth/tokens/helidon-user", null, null);
        String body = readAndClose(response);
        assertThat(response.getStatus()).withFailMessage(body).isEqualTo(200);
        JsonNode payload = objectMapper.readTree(body);
        String token = payload.path("data").path("token").asText();
        assertThat(token).withFailMessage(body).isNotBlank();
        return token;
    }

    private Response request(String method, String path, String token, String body) {
        Invocation.Builder builder = target.path(path).request().header("X-Tenant-Id", "helidon-auth");
        if (Objects.nonNull(token)) {
            builder.header("Authorization", "Bearer " + token);
        }
        return Objects.isNull(body)
                ? builder.method(method)
                : builder.method(method, Entity.entity(body, MediaType.APPLICATION_JSON_TYPE));
    }

    private String readAndClose(Response response) {
        try (Response active = response) {
            return response.readEntity(String.class);
        }
    }
}
