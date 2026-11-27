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

import static org.junit.jupiter.api.Assertions.*;

/**
 * 三线地理编码在各自 HTTP 客户端下的本地协议与结果契约。
 */
class GeoBaiduHttpContractTest {

    /**
     * \u9a8c\u8bc1<code>requested.get().contains("address=%E6%9D%AD%E5%B7%9E+%E8%A5%BF%E6%B9%96")</code>\u4e3a\u771f\uff0c<code>requested.get().contains("ak=test-ak")</code>\u4e3a\u771f\uff0c<code>requested.get().contains("|accept=application/json")</code>\u4e3a\u771f\u3002
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
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

    /**
     * \u9a8c\u8bc1<code>result.isPresent()</code>\u4e3a\u771f\uff0c<code>result.get().getString("address")</code> \u7b49\u4e8e <code>"CN|\u6d59\u6c5f|\u676d\u5dde"</code>\uff0c<code>template.getLocationByIp(null)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
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

    /**
     * \u9a8c\u8bc1<code>template.getLocationByAddress("\u676d\u5dde").isPresent()</code>\u4e3a\u5047\uff0c<code>template.getLocationByIp("127.0.0.1").isPresent()</code>\u4e3a\u5047\u3002
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
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

    /**
     * \u9a8c\u8bc1<code>template.getLocationByAddress("\u676d\u5dde")</code> \u629b\u51fa <code>IOException.class</code>\uff0c<code>template.getLocationByIp("127.0.0.1").isPresent()</code>\u4e3a\u5047\u3002
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
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

    /**
     * \u521b\u5efa <code>new GeoBaiduTemplate</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param server \u4f20\u7ed9 <code>server.getAddress()</code> \u7684 <code>HttpServer</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new GeoBaiduTemplate("test-ak", base + "/geocoding?address=%s&amp;ak=%s", base + "/location?ak=%s&amp;ip=%s")</code>
     */
    private GeoBaiduTemplate template(HttpServer server) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new GeoBaiduTemplate("test-ak",
                base + "/geocoding?address=%s&ak=%s",
                base + "/location?ak=%s&ip=%s");
    }

    /**
     * \u8bfb\u53d6 <code>server</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param status \u5f53\u524d HTTP \u6216\u4e1a\u52a1\u6d41\u7a0b\u7684\u72b6\u6001
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @param requested \u4f20\u7ed9 <code>server.createContext()</code> \u7684 <code>AtomicReference&lt;String&gt;</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>server</code>
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
    private HttpServer server(int status, String body, AtomicReference<String> requested) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> reply(exchange, status, body, requested));
        server.start();
        return server;
    }

    /**
     * \u8c03\u7528 <code>requested.set()</code>\u3001<code>exchange.getRequestURI()</code>\u3001<code>exchange.getRequestHeaders()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param exchange \u5f53\u524d HTTP \u8bf7\u6c42\u53ca\u54cd\u5e94\u4ea4\u6362\u5bf9\u8c61
     * @param status \u5f53\u524d HTTP \u6216\u4e1a\u52a1\u6d41\u7a0b\u7684\u72b6\u6001
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @param requested \u4f20\u7ed9 <code>requested.set()</code> \u7684 <code>AtomicReference&lt;String&gt;</code> \u8f93\u5165
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
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
