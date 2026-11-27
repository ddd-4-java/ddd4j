package io.ddd4j.data.crypto.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.ddd4j.core.exception.BizRuntimeException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 三线加密服务在各自 HTTP 和 Jackson 适配下的本地请求与结果契约。
 */
class FlksecCryptoHttpContractTest {

    /**
     * \u9a8c\u8bc1<code>restored</code> \u7b49\u4e8e <code>"restored"</code>\u3002
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
    @Test
    void decryptUsesRemotePlaintextRatherThanInputCiphertext() throws IOException {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = server("/api/crypto/sysDecrypt",
                "{\"code\":200,\"data\":\"\\\"restored\\\"\"}", body);
        try {
            FlksecCryptoStrategy strategy = strategy(server);
            String restored = strategy.decrypt("ciphertext", null, "CBC", "PKCS7",
                    "test-key", "test-iv", false, String.class);
            assertEquals("restored", restored);
            assertTrue(body.get().contains("\"data\":\"ciphertext\""));
            assertTrue(body.get().contains("\"algorithmType\":\"sm4\""));
        } finally {
            server.stop(0);
        }
    }

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>HttpServer.create()</code>\u3001<code>server.createContext()</code>\u3001<code>reply()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
    @Test
    void encryptAndHmacUseExpectedEndpointsAndRemoteResult() throws IOException {
        AtomicReference<String> encryptBody = new AtomicReference<>();
        AtomicReference<String> hmacBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/crypto/sysEncrypt",
                exchange -> reply(exchange, "{\"code\":200,\"data\":\"encrypted\"}", encryptBody));
        server.createContext("/api/hmac/sm3hmac",
                exchange -> reply(exchange, "{\"code\":200,\"data\":\"signed\"}", hmacBody));
        server.start();
        try {
            FlksecCryptoStrategy strategy = strategy(server);
            assertEquals("encrypted", strategy.encrypt("plain", null, "CBC", "PKCS7",
                    "test-key", "test-iv", false));
            assertEquals("signed", strategy.hmac("plain", null, "test-key", "test-iv", false));
            assertTrue(encryptBody.get().contains("\"data\":\"\\\"plain\\\"\""));
            assertTrue(hmacBody.get().contains("\"data\":\"\\\"plain\\\"\""));
        } finally {
            server.stop(0);
        }
    }

    /**
     * \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>BizRuntimeException.class</code>\uff0c<code>error.getMessage()</code> \u7b49\u4e8e <code>"invalid-key"</code>\u3002
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
    @Test
    void remoteBusinessErrorIsPropagated() throws IOException {
        HttpServer server = server("/api/crypto/sysEncrypt",
                "{\"code\":400,\"msg\":\"invalid-key\"}", new AtomicReference<>());
        try {
            FlksecCryptoStrategy strategy = strategy(server);
            BizRuntimeException error = assertThrows(BizRuntimeException.class,
                    () -> strategy.encrypt("plain", null, "CBC", "PKCS7",
                            "test-key", "test-iv", false));
            assertEquals("invalid-key", error.getMessage());
        } finally {
            server.stop(0);
        }
    }

    /**
     * \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>HttpServer.create()</code>\u3001<code>server.createContext()</code>\u3001<code>reply()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
    @Test
    void encodedInputIsSentForEncryptAndHmac() throws IOException {
        AtomicReference<String> encryptBody = new AtomicReference<>();
        AtomicReference<String> hmacBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/crypto/sysEncrypt",
                exchange -> reply(exchange, "{\"code\":200,\"data\":\"encrypted\"}", encryptBody));
        server.createContext("/api/hmac/sm3hmac",
                exchange -> reply(exchange, "{\"code\":200,\"data\":\"signed\"}", hmacBody));
        server.start();
        try {
            FlksecCryptoStrategy strategy = strategy(server);
            assertEquals("encrypted", strategy.encrypt("plain", null, "CBC", "PKCS7",
                    "test-key", "test-iv", true));
            assertEquals("signed", strategy.hmac("plain", null, "test-key", "test-iv", true));
            String encoded = Base64.getEncoder().encodeToString("\"plain\"".getBytes(StandardCharsets.UTF_8));
            assertTrue(encryptBody.get().contains("\"data\":\"" + encoded + "\""));
            assertTrue(hmacBody.get().contains("\"data\":\"" + encoded + "\""));
            assertTrue(encryptBody.get().contains("\"plainIsEncode\":\"true\""));
            assertTrue(hmacBody.get().contains("\"plainIsEncode\":\"true\""));
        } finally {
            server.stop(0);
        }
    }

    /**
     * \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>BizRuntimeException.class</code>\u3002
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
    @Test
    void nonSuccessHttpStatusIsReportedForEveryEndpoint() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/api/crypto/sysEncrypt",
                exchange -> reply(exchange, "unavailable", body, 503));
        server.createContext("/api/crypto/sysDecrypt",
                exchange -> reply(exchange, "unavailable", body, 503));
        server.createContext("/api/hmac/sm3hmac",
                exchange -> reply(exchange, "unavailable", body, 503));
        server.start();
        try {
            FlksecCryptoStrategy strategy = strategy(server);
            assertEquals("调用远程接口加密失败，StatusCode :503", assertThrows(BizRuntimeException.class,
                    () -> strategy.encrypt("plain", null, "CBC", "PKCS7", "test-key", "test-iv", false)).getMessage());
            assertEquals("调用远程接口解密失败，StatusCode :503", assertThrows(BizRuntimeException.class,
                    () -> strategy.decrypt("ciphertext", null, "CBC", "PKCS7", "test-key", "test-iv", false,
                            String.class)).getMessage());
            assertEquals("调用远程接口签名失败，StatusCode :503", assertThrows(BizRuntimeException.class,
                    () -> strategy.hmac("plain", null, "test-key", "test-iv", false)).getMessage());
        } finally {
            server.stop(0);
        }
    }

    /**
     * \u521b\u5efa <code>new FlksecCryptoStrategy</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @param server \u4f20\u7ed9 <code>String.valueOf()</code> \u7684 <code>HttpServer</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new FlksecCryptoStrategy(new ObjectMapper(), "127.0.0.1", String.valueOf(server.getAddress().getPort()), "http")</code>
     */
    private FlksecCryptoStrategy strategy(HttpServer server) {
        return new FlksecCryptoStrategy(new ObjectMapper(), "127.0.0.1",
                String.valueOf(server.getAddress().getPort()), "http");
    }

    /**
     * \u8bfb\u53d6 <code>server</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param path \u6d4b\u8bd5\u8bf7\u6c42\u7684\u8d44\u6e90\u8def\u5f84\u6216\u5f85\u8bbf\u95ee\u6587\u4ef6\u8def\u5f84
     * @param response \u5f53\u524d\u8bf7\u6c42\u5df2\u6536\u5230\u7684\u54cd\u5e94
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>server</code>
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
    private HttpServer server(String path, String response, AtomicReference<String> body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> reply(exchange, response, body));
        server.start();
        return server;
    }

    /**
     * \u8c03\u7528 <code>reply()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param exchange \u5f53\u524d HTTP \u8bf7\u6c42\u53ca\u54cd\u5e94\u4ea4\u6362\u5bf9\u8c61
     * @param response \u5f53\u524d\u8bf7\u6c42\u5df2\u6536\u5230\u7684\u54cd\u5e94
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
    private void reply(HttpExchange exchange, String response, AtomicReference<String> body) throws IOException {
        reply(exchange, response, body, 200);
    }

    /**
     * \u8c03\u7528 <code>exchange.getRequestBody()</code>\u3001<code>body.set()</code>\u3001<code>Math.max()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param exchange \u5f53\u524d HTTP \u8bf7\u6c42\u53ca\u54cd\u5e94\u4ea4\u6362\u5bf9\u8c61
     * @param response \u5f53\u524d\u8bf7\u6c42\u5df2\u6536\u5230\u7684\u54cd\u5e94
     * @param body \u5f53\u524d\u8bf7\u6c42\u6216\u54cd\u5e94\u4e2d\u8981\u8bfb\u53d6\u7684\u6b63\u6587
     * @param status \u5f53\u524d HTTP \u6216\u4e1a\u52a1\u6d41\u7a0b\u7684\u72b6\u6001
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
    private void reply(HttpExchange exchange, String response, AtomicReference<String> body, int status) throws IOException {
        byte[] request = new byte[4096];
        int length = exchange.getRequestBody().read(request);
        body.set(new String(request, 0, Math.max(length, 0), StandardCharsets.UTF_8));
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
