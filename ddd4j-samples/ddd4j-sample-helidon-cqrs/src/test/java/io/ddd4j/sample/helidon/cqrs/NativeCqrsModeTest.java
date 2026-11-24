package io.ddd4j.sample.helidon.cqrs;

import io.helidon.microprofile.server.Server;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 原生启动入口的默认内存模式契约。 */
class NativeCqrsModeTest {

    @Test
    void disabledProjectionLeavesDurableRoutesUnregistered() {
        Server server = HelidonCqrsApplication.start(0);
        try {
            Client client = ClientBuilder.newClient();
            try {
                WebTarget base = client.target("http://127.0.0.1:" + server.port());
                try (Response orders = base.path("sample/orders").request().options();
                     Response projection = base.path("sample/projections/orders/run").request().options()) {
                    assertThat(orders.getStatus()).as("持久订单路由需要显式启用").isEqualTo(404);
                    assertThat(projection.getStatus()).as("手动投影路由需要显式启用").isEqualTo(404);
                }
                Map<String, String> order = new LinkedHashMap<>();
                order.put("orderNo", "HELIDON-MEMORY-001");
                order.put("buyerId", "buyer-1");
                order.put("buyerName", "Alice");
                try (Response created = base.path("orders").request().post(Entity.json(order))) {
                    assertThat(created.getStatus()).as("默认内存订单仍可创建").isEqualTo(201);
                }
            } finally {
                client.close();
            }
        } finally {
            server.stop();
        }
    }
}
