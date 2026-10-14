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
package io.ddd4j.sample.helidon.cqrs;

import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.helidon.microprofile.server.Server;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.enterprise.inject.spi.CDI;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.Map;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Helidon 订单 CQRS 集成测试。
 *
 * <p>CQRS 组件由 {@link HelidonCqrsBeans} 通过 {@code beans.xml}
 * 自动发现，无需 {@code @AddBean} 注解。
 */
@DisplayName("Helidon Order CQRS 集成测试")
class HelidonOrderCqrsIT {

    private static Server server;
    private static Client client;
    private static WebTarget webTarget;
    private static ViewManager viewManager;

    @BeforeAll
    static void startServer() {
        server = Server.builder().host("127.0.0.1").port(0)
                .addApplication(new HelidonCqrsJaxRsApplication()).build().start();
        client = ClientBuilder.newClient();
        webTarget = client.target("http://127.0.0.1:" + server.port());
        viewManager = CDI.current().select(ViewManager.class).get();
    }

    @AfterAll
    static void stopServer() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.stop();
        }
    }

    private static Map<String, String> request(String orderNo, String buyerId, String buyerName) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("orderNo", orderNo);
        body.put("buyerId", buyerId);
        body.put("buyerName", buyerName);
        return body;
    }

    @Test
    @DisplayName("POST /orders 创建订单 -> 返回 201 + orderId")
    void createOrder_thenProjection() {
        Response response = webTarget.path("/orders")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(request("ORD-001", "B001", "Alice")));

        assertThat(response.getStatus()).isEqualTo(201);
        Map<String, Object> body = response.readEntity(Map.class);
        assertThat((Boolean) body.get("success")).isTrue();
        assertThat((String) body.get("orderId")).isNotEmpty();
    }

    @Test
    @DisplayName("GET /orders/{id} 查询读模型 -> 返回正确数据")
    void getOrder_fromReadModel() {
        Response createResp = webTarget.path("/orders")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(request("ORD-002", "B002", "Bob")));
        String orderId = (String) createResp.readEntity(Map.class).get("orderId");

        // 触发投影
        viewManager.triggerOnce();

        Response getResp = webTarget.path("/orders/" + orderId)
                .request(MediaType.APPLICATION_JSON)
                .get();
        assertThat(getResp.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("幂等性：同一 orderNo 重复创建 -> 返回 409 Conflict")
    void createOrder_idempotent() {
        webTarget.path("/orders")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(request("ORD-003", "B003", "Charlie")));

        Response response = webTarget.path("/orders")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(request("ORD-003", "B003", "Charlie")));

        assertThat(response.getStatus()).isEqualTo(409);
    }
}
