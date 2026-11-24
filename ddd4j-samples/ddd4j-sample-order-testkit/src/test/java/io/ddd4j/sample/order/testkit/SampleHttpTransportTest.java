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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 真实 loopback 传输守护空体、JSON null、字符集、请求头及驱动错误语义。 */
class SampleHttpTransportTest {
    private HttpServer server;
    private String url;
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<List<String>> receivedTrace = new AtomicReference<>();
    private final AtomicReference<String> responseBody = new AtomicReference<>("ok");
    private final AtomicReference<Charset> responseCharset = new AtomicReference<>(StandardCharsets.UTF_8);
    private final AtomicInteger status = new AtomicInteger(200);

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

    @AfterEach
    void stopServer() {
        if (Objects.nonNull(server)) {
            server.stop(0);
        }
    }

    @Test
    void transportDistinguishesAbsentBodyFromJsonNull() throws Exception {
        SampleHttpTransport.request(url, "POST", Collections.emptyMap(), Collections.emptyMap(), null);
        assertEquals("", receivedBody.get());
        SampleHttpTransport.request(url, "POST", Collections.emptyMap(), Collections.emptyMap(), "null");
        assertEquals("null", receivedBody.get());
    }

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

    @Test
    void transportHonorsTheDeclaredResponseCharset() throws Exception {
        responseCharset.set(StandardCharsets.ISO_8859_1);
        responseBody.set("café");
        SampleHttpTransport.Response response = SampleHttpTransport.request(url, "GET",
                Collections.emptyMap(), Collections.emptyMap(), null);
        assertEquals("café", response.body());
    }

    @Test
    void driversKeepDistinctNullBodiesAndSecurityMessages() {
        EnvelopeOrderHttpDriver envelope = new EnvelopeOrderHttpDriver(url, "/orders", Collections.emptyMap());
        HttpOrderSampleDriver bare = new HttpOrderSampleDriver(url);
        responseBody.set("{\"data\":\"envelope\"}");
        assertEquals("envelope", envelope.request("POST", "/orders", null, Collections.emptyMap()).asText());
        assertEquals("", receivedBody.get());
        responseBody.set("\"bare\"");
        assertEquals("bare", bare.request("POST", "/orders", null, String.class));
        assertEquals("null", receivedBody.get());
        for (int denied : new int[] {401, 403}) {
            status.set(denied);
            responseBody.set("拒绝");
            SecurityException envelopeFailure = assertThrows(SecurityException.class,
                    () -> envelope.request("POST", "/orders", null, Collections.emptyMap()));
            SecurityException bareFailure = assertThrows(SecurityException.class,
                    () -> bare.request("POST", "/orders", null, String.class));
            assertEquals("HTTP " + denied, envelopeFailure.getMessage());
            assertEquals("拒绝", bareFailure.getMessage());
        }
    }

    @Test
    void envelopeDriverWrapsMalformedJsonWithItsCause() {
        responseBody.set("{\"data\":");
        EnvelopeOrderHttpDriver envelope = new EnvelopeOrderHttpDriver(url, "/orders", Collections.emptyMap());
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> envelope.request("GET", "/orders", null, Collections.emptyMap()));
        assertNotNull(failure.getCause());
    }

    @Test
    void bareDriverWrapsMalformedJsonWithItsCause() {
        responseBody.set("\"unterminated");
        HttpOrderSampleDriver bare = new HttpOrderSampleDriver(url);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> bare.request("GET", "/orders", null, String.class));
        assertNotNull(failure.getCause());
    }

    @Test
    void driversPreserveOtherHttpErrorBodies() {
        status.set(500);
        responseBody.set("服务异常");
        EnvelopeOrderHttpDriver envelope = new EnvelopeOrderHttpDriver(url, "/orders", Collections.emptyMap());
        HttpOrderSampleDriver bare = new HttpOrderSampleDriver(url);
        IllegalStateException envelopeFailure = assertThrows(IllegalStateException.class,
                () -> envelope.request("GET", "/orders", null, Collections.emptyMap()));
        IllegalStateException bareFailure = assertThrows(IllegalStateException.class,
                () -> bare.request("GET", "/orders", null, String.class));
        assertEquals("HTTP 500: 服务异常", envelopeFailure.getMessage());
        assertEquals("HTTP 500: 服务异常", bareFailure.getMessage());
    }

}
