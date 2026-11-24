package io.ddd4j.sample.order.testkit;

import com.sun.net.httpserver.HttpServer;
import io.ddd4j.sample.order.domain.OrderQuery;
import io.ddd4j.sample.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 真实 HTTP 查询保持可选条件和带分隔符的买家标识。 */
class EnvelopeOrderHttpDriverQueryTest {
    @Test void encodesBuyerAndOmitsAbsentFilters() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/orders", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            byte[] body = "{\"data\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (java.io.OutputStream output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            EnvelopeOrderHttpDriver driver = new EnvelopeOrderHttpDriver("http://127.0.0.1:" + server.getAddress().getPort(), "/orders", Collections.emptyMap());
            driver.query(new OrderQuery("买家 A&B=1+2", OrderStatus.DRAFT, 2, 10));
            assertEquals("page=2&size=10&buyerId=买家 A&B=1+2&status=DRAFT", URLDecoder.decode(query.get(), StandardCharsets.UTF_8.name()));
            assertEquals(4, query.get().split("&").length);
            driver.query(new OrderQuery(null, null, 1, 20));
            assertEquals("page=1&size=20", query.get());
        } finally { server.stop(0); }
    }
}
