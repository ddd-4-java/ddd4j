package io.ddd4j.sample.order.testkit;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 真实 loopback 传输守护空体、JSON null、字符集、请求头及错误响应。
 */
class SampleHttpTransportTest {
    /**
     * 测试服务收到的请求正文。
     */
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    /**
     * 测试服务收到的同名跟踪头值列表。
     */
    private final AtomicReference<List<String>> receivedTrace = new AtomicReference<>();
    /**
     * 测试服务下一次发送的响应正文。
     */
    private final AtomicReference<String> responseBody = new AtomicReference<>("ok");
    /**
     * 测试服务编码响应并声明给客户端的字符集。
     */
    private final AtomicReference<Charset> responseCharset = new AtomicReference<>(StandardCharsets.UTF_8);
    /**
     * 测试服务下一次发送的 HTTP 状态码。
     */
    private final AtomicInteger status = new AtomicInteger(200);
    /**
     * 当前测试使用的本地 HTTP 服务。
     */
    private HttpServer server;
    /**
     * 当前测试服务的 HTTP 请求地址。
     */
    private String url;

    /**
     * 启动使用动态本地端口的 HTTP 测试服务。
     *
     * @throws IOException 无法绑定或启动本地 HTTP 服务
     */
    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (Scanner scanner = new Scanner(exchange.getRequestBody(), StandardCharsets.UTF_8.name())) {
                scanner.useDelimiter("\\A");
                receivedBody.set(scanner.hasNext() ? scanner.next() : "");
            }
            List<String> trace = exchange.getRequestHeaders().get("X-Trace");
            receivedTrace.set(Objects.isNull(trace) ? Collections.emptyList() : new ArrayList<>(trace));
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=" + responseCharset.get().name());
            if (status.get() == 204) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            byte[] bytes = responseBody.get().getBytes(responseCharset.get());
            exchange.sendResponseHeaders(status.get(), bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * 关闭当前测试启动的 HTTP 服务。
     */
    @AfterEach
    void stopServer() {
        if (Objects.nonNull(server)) {
            server.stop(0);
        }
    }

    /**
     * 验证传输层区分缺省正文和显式 JSON null。
     *
     * @throws Exception 请求传输或响应读取失败
     */
    @Test
    void transportDistinguishesAbsentBodyFromJsonNull() throws Exception {
        SampleHttpTransport.request(url, "POST", Collections.emptyMap(), Collections.emptyMap(), null);
        assertEquals("", receivedBody.get());
        SampleHttpTransport.request(url, "POST", Collections.emptyMap(), Collections.emptyMap(), "null");
        assertEquals("null", receivedBody.get());
    }

    /**
     * 验证 UTF-8 正文及重复追加的同名头在传输后保持原值。
     *
     * @throws Exception 请求传输或响应读取失败
     */
    @Test
    void transportPreservesUtf8AndAdditiveHeaders() throws Exception {
        responseBody.set("响应：中文");
        status.set(201);
        SampleHttpTransport.Response response = SampleHttpTransport.request(url, "POST",
                Collections.singletonMap("X-Trace", "default"),
                Collections.singletonMap("X-Trace", "additional"), "\"请求：中文\"");
        assertEquals("\"请求：中文\"", receivedBody.get());
        assertEquals(Arrays.asList("default", "additional"), receivedTrace.get());
        assertEquals(201, response.statusCode());
        assertEquals("响应：中文", response.body());
    }

    /**
     * 验证传输层原样返回空响应和错误状态及正文。
     *
     * @throws Exception 请求传输或响应读取失败
     */
    @Test
    void transportReturnsEmptyAndErrorResponsesWithoutDriverPolicy() throws Exception {
        status.set(204);
        SampleHttpTransport.Response empty = SampleHttpTransport.request(url, "GET",
                Collections.emptyMap(), Collections.emptyMap(), null);
        assertEquals(204, empty.statusCode());
        assertEquals("", empty.body());
        status.set(403);
        responseBody.set("拒绝");
        SampleHttpTransport.Response denied = SampleHttpTransport.request(url, "GET",
                Collections.emptyMap(), Collections.emptyMap(), null);
        assertEquals(403, denied.statusCode());
        assertEquals("拒绝", denied.body());
    }

    /**
     * 验证按 HTTP 声明的响应字符集解码正文。
     *
     * @throws Exception 请求传输或响应读取失败
     */
    @Test
    void transportHonorsTheDeclaredResponseCharset() throws Exception {
        responseCharset.set(StandardCharsets.ISO_8859_1);
        responseBody.set("café");
        SampleHttpTransport.Response response = SampleHttpTransport.request(url, "GET",
                Collections.emptyMap(), Collections.emptyMap(), null);
        assertEquals("café", response.body());
    }
}
