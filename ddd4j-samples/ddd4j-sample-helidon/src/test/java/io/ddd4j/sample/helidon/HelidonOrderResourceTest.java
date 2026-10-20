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
import io.helidon.microprofile.server.Server;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class HelidonOrderResourceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static Server server;
    private static Client httpClient;
    private static WebTarget target;

    @BeforeAll
    static void startServer() {
        System.setProperty("ddd4j.web.public-paths", "/api/auth/**");
        server = Server.builder().host("127.0.0.1").port(0)
                .addApplication(new HelidonOrderApplication()).build().start();
        httpClient = ClientBuilder.newClient();
        target = httpClient.target("http://127.0.0.1:" + server.port());
    }

    @AfterAll
    static void stopServer() {
        if (httpClient != null) {
            httpClient.close();
        }
        if (server != null) {
            server.stop();
        }
        System.clearProperty("ddd4j.web.public-paths");
    }

    @Test
    void shouldRunSharedOrderUseCasesThroughHelidonHttp() throws Exception {
        String token = issueToken();
        Response create = request("POST", "/api/orders", token,
                "{\"orderNo\":\"HELIDON-001\",\"buyerId\":\"buyer-1\",\"buyerName\":\"Alice\"}");
        String createBody = readAndClose(create);
        assertThat(create.getStatus()).withFailMessage(createBody).isEqualTo(201);
        String orderId = objectMapper.readTree(createBody).path("data").path("id").asText();
        assertThat(orderId).withFailMessage(createBody).isNotBlank();

        Response line = request("POST", "/api/orders/" + orderId + "/lines", token,
                "{\"goodsId\":\"goods-1\",\"goodsName\":\"DDD Book\",\"quantity\":2,\"unitPrice\":59.90}");
        String lineBody = readAndClose(line);
        assertThat(line.getStatus()).withFailMessage(lineBody).isEqualTo(200);
        assertThat(objectMapper.readTree(lineBody).path("data").path("totalAmount").decimalValue())
                .isEqualByComparingTo("119.80");

        Response missingKey = request("POST", "/api/orders/" + orderId + "/pay", token, null);
        String missingKeyBody = readAndClose(missingKey);
        assertThat(missingKey.getStatus()).withFailMessage(missingKeyBody).isEqualTo(400);

        Invocation.Builder payment = target.path("/api/orders/" + orderId + "/pay").request()
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "helidon-payment-001");
        Response paid = payment.post(Entity.entity("", MediaType.APPLICATION_JSON_TYPE));
        String paidBody = readAndClose(paid);
        assertThat(paid.getStatus()).withFailMessage(paidBody).isEqualTo(200);
        assertThat(objectMapper.readTree(paidBody).path("data").path("status").asText()).isEqualTo("PAID");
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
        Invocation.Builder builder = target.path(path).request();
        if (Objects.nonNull(token)) {
            builder.header("Authorization", "Bearer " + token);
        }
        return Objects.isNull(body)
                ? builder.method(method)
                : builder.method(method, Entity.entity(body, MediaType.APPLICATION_JSON_TYPE));
    }

    private String readAndClose(Response response) {
        try (Response closeable = response) {
            return response.readEntity(String.class);
        }
    }
}
