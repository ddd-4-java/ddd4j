package io.ddd4j.data.external.geo;

import com.alibaba.fastjson2.JSONObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 三线地理编码在各自 HTTP 客户端下的本地协议与结果契约。 */
class GeoBaiduHttpContractTest {

    @Test
    void addressLookupEncodesInputAndParsesCoordinates() throws IOException {
        AtomicReference<String> requested = new AtomicReference<>();
        HttpServer server = server(200,
                "{\"status\":0,\"result\":{\"location\":{\"lat\":30.1,\"lng\":120.2}}}", requested);
        try {
            GeoBaiduTemplate template = template(server);

            Map<String, BigDecimal> coordinates = template.getLatAndLngByAddress("杭州 西湖");

            assertEquals(new BigDecimal("30.1"), coordinates.get("lat"));
            assertEquals(new BigDecimal("120.2"), coordinates.get("lng"));
            assertTrue(requested.get().contains("address=%E6%9D%AD%E5%B7%9E+%E8%A5%BF%E6%B9%96"));
            assertTrue(requested.get().contains("ak=test-ak"));
            assertTrue(requested.get().contains("|accept=application/json"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void ipLookupParsesSuccessAndRejectsNull() throws IOException {
        AtomicReference<String> requested = new AtomicReference<>();
        HttpServer server = server(200, "{\"status\":0,\"address\":\"CN|浙江|杭州\"}", requested);
        try {
            GeoBaiduTemplate template = template(server);

            Optional<JSONObject> result = template.getLocationByIp("127.0.0.1");

            assertTrue(result.isPresent());
            assertEquals("CN|浙江|杭州", result.get().getString("address"));
            assertTrue(requested.get().contains("ip=127.0.0.1"));
            assertThrows(NullPointerException.class, () -> template.getLocationByIp(null));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failedHttpResponseReturnsEmptyLocation() throws IOException {
        AtomicReference<String> requested = new AtomicReference<>();
        HttpServer server = server(503, "unavailable", requested);
        try {
            GeoBaiduTemplate template = template(server);

            assertFalse(template.getLocationByAddress("杭州").isPresent());
            assertFalse(template.getLocationByIp("127.0.0.1").isPresent());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void providerErrorKeepsAddressExceptionAndIpFallback() throws IOException {
        AtomicReference<String> requested = new AtomicReference<>();
        HttpServer server = server(200, "{\"status\":220,\"message\":\"invalid-ak\"}", requested);
        try {
            GeoBaiduTemplate template = template(server);

            assertEquals("invalid-ak",
                    assertThrows(IOException.class, () -> template.getLocationByAddress("杭州")).getMessage());
            assertFalse(template.getLocationByIp("127.0.0.1").isPresent());
        } finally {
            server.stop(0);
        }
    }

    private GeoBaiduTemplate template(HttpServer server) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new GeoBaiduTemplate("test-ak",
                base + "/geocoding?address=%s&ak=%s",
                base + "/location?ak=%s&ip=%s");
    }

    private HttpServer server(int status, String body, AtomicReference<String> requested) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> reply(exchange, status, body, requested));
        server.start();
        return server;
    }

    private void reply(HttpExchange exchange, int status, String body,
                       AtomicReference<String> requested) throws IOException {
        requested.set(exchange.getRequestURI().toString()
                + "|accept=" + exchange.getRequestHeaders().getFirst("Accept"));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
