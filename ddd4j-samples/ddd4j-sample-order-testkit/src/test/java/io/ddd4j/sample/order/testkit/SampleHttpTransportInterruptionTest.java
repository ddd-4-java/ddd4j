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
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * 关闭当前测试启动的 HTTP 服务。
     */
    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    /**
     * 验证外部传输入口将中断报告为 I/O 异常并恢复线程中断标记。
     */
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
     * 验证对端尚未响应时中断调用方会关闭活动连接。
     *
     * @throws Exception 测试连接、等待或线程结果获取失败
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
