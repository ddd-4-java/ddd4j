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
     * 验证<code>restored</code> 等于 <code>"restored"</code>。
     *
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
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
     * 在测试框架中执行 <code>HttpServer.create()</code>、<code>server.createContext()</code>、<code>reply()</code>，验证该场景。
     *
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
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
     * 验证被测调用 抛出 <code>BizRuntimeException.class</code>，<code>error.getMessage()</code> 等于 <code>"invalid-key"</code>。
     *
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
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
     * 在测试框架中执行 <code>HttpServer.create()</code>、<code>server.createContext()</code>、<code>reply()</code>，验证该场景。
     *
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
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
     * 验证被测调用 抛出 <code>BizRuntimeException.class</code>。
     *
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
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
     * 创建 <code>new FlksecCryptoStrategy</code> 供当前测试路径调用。
     *
     * @param server 传给 <code>String.valueOf()</code> 的 <code>HttpServer</code> 输入
     * @return 当前测试使用的结果，取自 <code>new FlksecCryptoStrategy(new ObjectMapper(), "127.0.0.1", String.valueOf(server.getAddress().getPort()), "http")</code>
     */
    private FlksecCryptoStrategy strategy(HttpServer server) {
        return new FlksecCryptoStrategy(new ObjectMapper(), "127.0.0.1",
                String.valueOf(server.getAddress().getPort()), "http");
    }

    /**
     * 读取 <code>server</code> 供当前测试使用。
     *
     * @param path     测试请求的资源路径或待访问文件路径
     * @param response 当前请求已收到的响应
     * @param body     当前请求或响应中要读取的正文
     * @return 当前测试使用的结果，取自 <code>server</code>
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
     */
    private HttpServer server(String path, String response, AtomicReference<String> body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> reply(exchange, response, body));
        server.start();
        return server;
    }

    /**
     * 调用 <code>reply()</code> 执行当前夹具操作。
     *
     * @param exchange 当前 HTTP 请求及响应交换对象
     * @param response 当前请求已收到的响应
     * @param body     当前请求或响应中要读取的正文
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
     */
    private void reply(HttpExchange exchange, String response, AtomicReference<String> body) throws IOException {
        reply(exchange, response, body, 200);
    }

    /**
     * 调用 <code>exchange.getRequestBody()</code>、<code>body.set()</code>、<code>Math.max()</code> 执行当前夹具操作。
     *
     * @param exchange 当前 HTTP 请求及响应交换对象
     * @param response 当前请求已收到的响应
     * @param body     当前请求或响应中要读取的正文
     * @param status   当前 HTTP 或业务流程的状态
     * @throws IOException 读取或写入测试数据时发生 I/O 错误
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
