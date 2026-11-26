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

@Testcontainers
class MicronautOrderControllerTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static EmbeddedServer start(String mode) {
        return ApplicationContext.run(EmbeddedServer.class, Map.of("micronaut.server.port", -1,
                "ddd4j.web.default-authentication-mode", mode, "ddd4j.web.public-paths", List.of("/api/auth/**"),
                "catalog.jdbc.url", MYSQL.getJdbcUrl(), "catalog.db.user", MYSQL.getUsername(),
                "catalog.db.password", MYSQL.getPassword()));
    }

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

    private String issueToken(HttpClient client, String baseUrl) throws Exception {
        HttpResponse<String> response = request(client, baseUrl, "POST", "/api/auth/tokens/micronaut-user", null, null, "auth");
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(response.statusCode()).isEqualTo(200);
        String token = body.path("data").path("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }

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
