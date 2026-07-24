package io.ddd4j.extension.monitor.channel.wecom;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link WeComClient} 集成测试。
 *
 * <p>使用 JDK 自带的 {@link HttpServer} mock 企业微信 webhook，验证：
 * <ul>
 *   <li>POST 到正确 URL（含 {@code key}）</li>
 *   <li>请求体是 JSON 且 {@code msgtype=markdown}</li>
 *   <li>JSON 里的 {@code markdown.content} 是原始 markdown 文本</li>
 *   <li>{@code at} 字段存在（默认 none）</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class WeComClientTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/cgi-bin/webhook/send", new Recorder());
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (Objects.nonNull(server)) {
            server.stop(0);
        }
    }

    @Test
    void sendMarkdownShouldPostValidJsonPayload() throws Exception {
        // 客户端指向 mock server，key 自定
        WeComClient client = new WeComClient("wehook_key_test",
                "http://127.0.0.1:" + port + "/cgi-bin/webhook/send?key=");
        client.sendMarkdown("**hello** wecom");
        Thread.sleep(100);

        String method = Recorder.lastMethod;
        String query = Recorder.lastQuery;
        String body = Recorder.lastBody;

        assertThat(method).isEqualTo("POST");
        assertThat(query).isEqualTo("key=wehook_key_test");
        assertThat(body)
                .contains("\"msgtype\":\"markdown\"")
                .contains("\"markdown\":")
                .contains("\"content\":\"**hello** wecom\"")
                .contains("\"at\":");
    }

    /**
     * 简易 recorder，记录最近一次请求的 method / query / body。
     */
    static class Recorder implements HttpHandler {
        static volatile String lastMethod;
        static volatile String lastQuery;
        static volatile String lastBody;

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            lastMethod = exchange.getRequestMethod();
            URI uri = exchange.getRequestURI();
            lastQuery = uri.getRawQuery();
            try (InputStream is = exchange.getRequestBody()) {
                lastBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            byte[] resp = "{\"errcode\":0,\"errmsg\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        }
    }
}
