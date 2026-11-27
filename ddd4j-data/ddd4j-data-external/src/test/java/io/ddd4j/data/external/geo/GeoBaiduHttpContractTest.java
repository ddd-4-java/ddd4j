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
     * 验证<code>requested.get().contains("address=%E6%9D%AD%E5%B7%9E+%E8%A5%BF%E6%B9%96")</code>为真，<code>requested.get().contains("ak=test-ak")</code>为真，<code>requested.get().contains("|accept=application/json")</code>为真。
     *
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
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
     * 验证<code>result.isPresent()</code>为真，<code>result.get().getString("address")</code> 等于 <code>"CN|浙江|杭州"</code>，<code>template.getLocationByIp(null)</code> 抛出 <code>NullPointerException.class</code>。
     *
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
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
     * 验证<code>template.getLocationByAddress("杭州").isPresent()</code>为假，<code>template.getLocationByIp("127.0.0.1").isPresent()</code>为假。
     *
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
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
     * 验证<code>template.getLocationByAddress("杭州")</code> 抛出 <code>IOException.class</code>，<code>template.getLocationByIp("127.0.0.1").isPresent()</code>为假。
     *
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
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
     * 创建 <code>new GeoBaiduTemplate</code> 供当前测试路径调用。
     *
     * @param server 传给 <code>server.getAddress()</code> 的 <code>HttpServer</code> 输入
     * @return 当前测试使用的结果，取自 <code>new GeoBaiduTemplate("test-ak", base + "/geocoding?address=%s&amp;ak=%s", base + "/location?ak=%s&amp;ip=%s")</code>
     */
    private GeoBaiduTemplate template(HttpServer server) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new GeoBaiduTemplate("test-ak",
                base + "/geocoding?address=%s&ak=%s",
                base + "/location?ak=%s&ip=%s");
    }

    /**
     * 读取 <code>server</code> 供当前测试使用。
     *
     * @param status    当前 HTTP 或业务流程的状态
     * @param body      当前请求或响应中要读取的正文
     * @param requested 传给 <code>server.createContext()</code> 的 <code>AtomicReference&lt;String&gt;</code> 输入
     * @return 当前测试使用的结果，取自 <code>server</code>
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
     */
    private HttpServer server(int status, String body, AtomicReference<String> requested) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> reply(exchange, status, body, requested));
        server.start();
        return server;
    }

    /**
     * 调用 <code>requested.set()</code>、<code>exchange.getRequestURI()</code>、<code>exchange.getRequestHeaders()</code> 执行当前夹具操作。
     *
     * @param exchange  当前 HTTP 请求及响应交换对象
     * @param status    当前 HTTP 或业务流程的状态
     * @param body      当前请求或响应中要读取的正文
     * @param requested 传给 <code>requested.set()</code> 的 <code>AtomicReference&lt;String&gt;</code> 输入
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
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
