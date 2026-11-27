package io.ddd4j.sample.order.testkit;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实 HTTP 调用在中断前后保持原生异常与线程标记。
 */
class SampleHttpTransportInterruptionTest {
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
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** \u5173\u95ed\u5f53\u524d\u6d4b\u8bd5\u542f\u52a8\u7684 HTTP \u670d\u52a1\u3002 */
    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    /** \u9a8c\u8bc1\u5916\u90e8\u4f20\u8f93\u5165\u53e3\u5c06\u4e2d\u65ad\u62a5\u544a\u4e3a I/O \u5f02\u5e38\u5e76\u6062\u590d\u7ebf\u7a0b\u4e2d\u65ad\u6807\u8bb0\u3002 */
    @Test
    void nativePublicTransportConvertsInterruptionToIoAndRestoresFlag() {
        Thread.currentThread().interrupt();
        try {
            IOException failure = assertThrows(IOException.class,
                    () -> SampleHttpTransport.request(url, "GET", Collections.emptyMap(), Collections.emptyMap(), null));
            assertTrue(failure.getCause() instanceof InterruptedException);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    /**
     * \u9a8c\u8bc1\u5bf9\u7aef\u5c1a\u672a\u54cd\u5e94\u65f6\u4e2d\u65ad\u8c03\u7528\u65b9\u4f1a\u5173\u95ed\u6d3b\u52a8\u8fde\u63a5\u3002
     * @throws Exception \u6d4b\u8bd5\u8fde\u63a5\u3001\u7b49\u5f85\u6216\u7ebf\u7a0b\u7ed3\u679c\u83b7\u53d6\u5931\u8d25
     */
    @Test
    void interruptClosesActiveConnectionBeforeThePeerResponds() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            listener.setSoTimeout(5000);
            FutureTask<Boolean> peerResult = new FutureTask<>(() -> {
                try (Socket peer = listener.accept();
                     BufferedReader input = new BufferedReader(new InputStreamReader(
                             peer.getInputStream(), StandardCharsets.US_ASCII))) {
                    peer.setSoTimeout(5000);
                    assertTrue(input.readLine().startsWith("GET /waiting "));
                    String header = input.readLine();
                    while (header != null && !header.isEmpty()) {
                        header = input.readLine();
                    }
                    assertEquals("", header);
                    received.countDown();
                    try {
                        return input.read() == -1;
                    } catch (SocketException closed) {
                        return true;
                    }
                }
            });
            Thread peer = new Thread(peerResult, "sample-http-test-peer");
            peer.setDaemon(true);
            peer.start();

            String blockedUrl = "http://127.0.0.1:" + listener.getLocalPort() + "/waiting";
            FutureTask<Boolean> callerResult = new FutureTask<>(() -> {
                IOException failure = assertThrows(IOException.class, () -> SampleHttpTransport.request(
                        blockedUrl, "GET", Collections.emptyMap(), Collections.emptyMap(), null));
                assertTrue(failure.getCause() instanceof InterruptedException);
                return Thread.currentThread().isInterrupted();
            });
            Thread caller = new Thread(callerResult, "sample-http-test-caller");
            caller.setDaemon(true);
            caller.start();
            try {
                assertTrue(received.await(5, TimeUnit.SECONDS));
                caller.interrupt();
                assertTrue(callerResult.get(3, TimeUnit.SECONDS));
                assertTrue(peerResult.get(6, TimeUnit.SECONDS));
            } finally {
                caller.interrupt();
                caller.join(5000);
                peer.join(5000);
            }
        }
    }
}
