package io.ddd4j.sample.order.testkit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
    /** \u6d4b\u8bd5\u670d\u52a1\u6536\u5230\u7684\u8bf7\u6c42\u6b63\u6587\u3002 */
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    /** \u6d4b\u8bd5\u670d\u52a1\u6536\u5230\u7684\u540c\u540d\u8ddf\u8e2a\u5934\u503c\u5217\u8868\u3002 */
    private final AtomicReference<List<String>> receivedTrace = new AtomicReference<>();
    /** \u6d4b\u8bd5\u670d\u52a1\u4e0b\u4e00\u6b21\u53d1\u9001\u7684\u54cd\u5e94\u6b63\u6587\u3002 */
    private final AtomicReference<String> responseBody = new AtomicReference<>("ok");
    /** \u6d4b\u8bd5\u670d\u52a1\u7f16\u7801\u54cd\u5e94\u5e76\u58f0\u660e\u7ed9\u5ba2\u6237\u7aef\u7684\u5b57\u7b26\u96c6\u3002 */
    private final AtomicReference<Charset> responseCharset = new AtomicReference<>(StandardCharsets.UTF_8);
    /** \u6d4b\u8bd5\u670d\u52a1\u4e0b\u4e00\u6b21\u53d1\u9001\u7684 HTTP \u72b6\u6001\u7801\u3002 */
    private final AtomicInteger status = new AtomicInteger(200);
    /** \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u672c\u5730 HTTP \u670d\u52a1\u3002 */
    private HttpServer server;
    /** \u5f53\u524d\u6d4b\u8bd5\u670d\u52a1\u7684 HTTP \u8bf7\u6c42\u5730\u5740\u3002 */
    private String url;

    /**
     * \u542f\u52a8\u4f7f\u7528\u52a8\u6001\u672c\u5730\u7aef\u53e3\u7684 HTTP \u6d4b\u8bd5\u670d\u52a1\u3002
     * @throws IOException \u65e0\u6cd5\u7ed1\u5b9a\u6216\u542f\u52a8\u672c\u5730 HTTP \u670d\u52a1
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

    /** \u5173\u95ed\u5f53\u524d\u6d4b\u8bd5\u542f\u52a8\u7684 HTTP \u670d\u52a1\u3002 */
    @AfterEach
    void stopServer() {
        if (Objects.nonNull(server)) {
            server.stop(0);
        }
    }

    /**
     * \u9a8c\u8bc1\u4f20\u8f93\u5c42\u533a\u5206\u7f3a\u7701\u6b63\u6587\u548c\u663e\u5f0f JSON null\u3002
     * @throws Exception \u8bf7\u6c42\u4f20\u8f93\u6216\u54cd\u5e94\u8bfb\u53d6\u5931\u8d25
     */
    @Test
    void transportDistinguishesAbsentBodyFromJsonNull() throws Exception {
        SampleHttpTransport.request(url, "POST", Collections.emptyMap(), Collections.emptyMap(), null);
        assertEquals("", receivedBody.get());
        SampleHttpTransport.request(url, "POST", Collections.emptyMap(), Collections.emptyMap(), "null");
        assertEquals("null", receivedBody.get());
    }

    /**
     * \u9a8c\u8bc1 UTF-8 \u6b63\u6587\u53ca\u91cd\u590d\u8ffd\u52a0\u7684\u540c\u540d\u5934\u5728\u4f20\u8f93\u540e\u4fdd\u6301\u539f\u503c\u3002
     * @throws Exception \u8bf7\u6c42\u4f20\u8f93\u6216\u54cd\u5e94\u8bfb\u53d6\u5931\u8d25
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
     * \u9a8c\u8bc1\u4f20\u8f93\u5c42\u539f\u6837\u8fd4\u56de\u7a7a\u54cd\u5e94\u548c\u9519\u8bef\u72b6\u6001\u53ca\u6b63\u6587\u3002
     * @throws Exception \u8bf7\u6c42\u4f20\u8f93\u6216\u54cd\u5e94\u8bfb\u53d6\u5931\u8d25
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
     * \u9a8c\u8bc1\u6309 HTTP \u58f0\u660e\u7684\u54cd\u5e94\u5b57\u7b26\u96c6\u89e3\u7801\u6b63\u6587\u3002
     * @throws Exception \u8bf7\u6c42\u4f20\u8f93\u6216\u54cd\u5e94\u8bfb\u53d6\u5931\u8d25
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
