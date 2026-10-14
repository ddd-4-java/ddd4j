package io.ddd4j.data.crypto.strategy;

import tools.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.ddd4j.core.exception.BizRuntimeException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 三线加密服务在各自 HTTP 和 Jackson 适配下的本地请求与结果契约。 */
class FlksecCryptoHttpContractTest {

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

    private FlksecCryptoStrategy strategy(HttpServer server) {
        return new FlksecCryptoStrategy(new ObjectMapper(), "127.0.0.1",
                String.valueOf(server.getAddress().getPort()), "http");
    }

    private HttpServer server(String path, String response, AtomicReference<String> body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> reply(exchange, response, body));
        server.start();
        return server;
    }

    private void reply(HttpExchange exchange, String response, AtomicReference<String> body) throws IOException {
        byte[] request = new byte[4096];
        int length = exchange.getRequestBody().read(request);
        body.set(new String(request, 0, Math.max(length, 0), StandardCharsets.UTF_8));
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
