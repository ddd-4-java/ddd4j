package io.ddd4j.sample.order.testkit;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 真实 HTTP 调用在中断前后保持原生异常与线程标记。 */
class SampleHttpTransportInterruptionTest {
    private HttpServer server;
    private String url;

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

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void nativeDriversRestoreInterruptAndKeepTheOriginalCause() {
        EnvelopeOrderHttpDriver envelope = new EnvelopeOrderHttpDriver(url, "/orders", Collections.emptyMap());
        HttpOrderSampleDriver bare = new HttpOrderSampleDriver(url);
        Runnable[] calls = {
                () -> envelope.request("GET", "/orders", null, Collections.emptyMap()),
                () -> bare.request("GET", "/orders", null, String.class)
        };
        for (Runnable call : calls) {
            Thread.currentThread().interrupt();
            try {
                IllegalStateException failure = assertThrows(IllegalStateException.class, call::run);
                assertTrue(failure.getCause() instanceof InterruptedException);
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        }
    }

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
