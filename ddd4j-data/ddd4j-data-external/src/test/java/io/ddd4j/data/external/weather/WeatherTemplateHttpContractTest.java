package io.ddd4j.data.external.weather;

import com.alibaba.fastjson2.JSONObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 三线天气查询的本地 HTTP、JSON 与缓存契约。
 */
class WeatherTemplateHttpContractTest {

    /**
     * \u9a8c\u8bc1<code>first</code>\u975e\u7a7a\uff0c<code>first.getString("city")</code> \u7b49\u4e8e <code>"ok"</code>\uff0c<code>requests.get()</code> \u7b49\u4e8e <code>1</code>\u3002
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
    @Test
    void successfulResponseIsParsedAndCached() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(200, "{\"city\":\"ok\"}", requests);
        try {
            WeatherTemplate template = new WeatherTemplate(urlTemplate(server));
            String cityCode = UUID.randomUUID().toString();

            JSONObject first = template.getWeather(cityCode);
            JSONObject second = template.getWeather(cityCode);

            assertNotNull(first);
            assertEquals("ok", first.getString("city"));
            assertEquals(first, second);
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    /**
     * \u9a8c\u8bc1<code>template.getWeather(cityCode)</code>\u4e3a\u7a7a\uff0c<code>requests.get()</code> \u7b49\u4e8e <code>2</code>\u3002
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
    @Test
    void failedResponseIsNotCached() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(503, "unavailable", requests);
        try {
            WeatherTemplate template = new WeatherTemplate(urlTemplate(server));
            String cityCode = UUID.randomUUID().toString();

            assertNull(template.getWeather(cityCode));
            assertNull(template.getWeather(cityCode));
            assertEquals(2, requests.get());
        } finally {
            server.stop(0);
        }
    }

    /**
     * \u8bfb\u53d6 <code>server</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param status \u5f53\u524d HTTP \u6216\u4e1a\u52a1\u6d41\u7a0b\u7684\u72b6\u6001
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @param requests \u4f20\u7ed9 <code>server.createContext()</code> \u7684 <code>AtomicInteger</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>server</code>
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
    private HttpServer server(int status, String body, AtomicInteger requests) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/weather/city/", exchange -> reply(exchange, status, body, requests));
        server.start();
        return server;
    }

    /**
     * \u8c03\u7528 <code>server.getAddress()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param server \u4f20\u7ed9 <code>server.getAddress()</code> \u7684 <code>HttpServer</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>"http://127.0.0.1:" + server.getAddress().getPort() + "/weather/city/%s"</code>
     */
    private String urlTemplate(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/weather/city/%s";
    }

    /**
     * \u8c03\u7528 <code>requests.incrementAndGet()</code>\u3001<code>body.getBytes()</code>\u3001<code>exchange.getResponseHeaders()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param exchange \u5f53\u524d HTTP \u8bf7\u6c42\u53ca\u54cd\u5e94\u4ea4\u6362\u5bf9\u8c61
     * @param status \u5f53\u524d HTTP \u6216\u4e1a\u52a1\u6d41\u7a0b\u7684\u72b6\u6001
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @param requests \u4f20\u7ed9 <code>requests.incrementAndGet()</code> \u7684 <code>AtomicInteger</code> \u8f93\u5165
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
    private void reply(HttpExchange exchange, int status, String body, AtomicInteger requests) throws IOException {
        requests.incrementAndGet();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
