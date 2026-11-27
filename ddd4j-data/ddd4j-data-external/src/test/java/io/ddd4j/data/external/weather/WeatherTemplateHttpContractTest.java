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
     * 验证<code>first</code>非空，<code>first.getString("city")</code> 等于 <code>"ok"</code>，<code>requests.get()</code> 等于 <code>1</code>。
     *
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
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
     * 验证<code>template.getWeather(cityCode)</code>为空，<code>requests.get()</code> 等于 <code>2</code>。
     *
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
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
     * 读取 <code>server</code> 供当前测试使用。
     *
     * @param status   当前 HTTP 或业务流程的状态
     * @param body     当前请求或响应中要读取的正文
     * @param requests 传给 <code>server.createContext()</code> 的 <code>AtomicInteger</code> 输入
     * @return 当前测试使用的结果，取自 <code>server</code>
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
     */
    private HttpServer server(int status, String body, AtomicInteger requests) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/weather/city/", exchange -> reply(exchange, status, body, requests));
        server.start();
        return server;
    }

    /**
     * 调用 <code>server.getAddress()</code> 执行当前夹具操作。
     *
     * @param server 传给 <code>server.getAddress()</code> 的 <code>HttpServer</code> 输入
     * @return 当前测试使用的结果，取自 <code>"http://127.0.0.1:" + server.getAddress().getPort() + "/weather/city/%s"</code>
     */
    private String urlTemplate(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/weather/city/%s";
    }

    /**
     * 调用 <code>requests.incrementAndGet()</code>、<code>body.getBytes()</code>、<code>exchange.getResponseHeaders()</code> 执行当前夹具操作。
     *
     * @param exchange 当前 HTTP 请求及响应交换对象
     * @param status   当前 HTTP 或业务流程的状态
     * @param body     当前请求或响应中要读取的正文
     * @param requests 传给 <code>requests.incrementAndGet()</code> 的 <code>AtomicInteger</code> 输入
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
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
