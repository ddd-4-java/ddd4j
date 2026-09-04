package io.ddd4j.extension.monitor.channel.dingtalk;

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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DingTalkClient} 集成测试。
 *
 * <p>使用 JDK 自带的 {@link HttpServer} mock 钉钉 webhook，验证：
 * <ul>
 *   <li>POST 到目标 URL（含 access_token / timestamp / sign）</li>
 *   <li>请求体 payload 与传入一致</li>
 *   <li>签名满足 {@code "timestamp\n" + secret} → HmacSHA256 → Base64 → URL-encode</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class DingTalkClientTest {

    private static final String SECRET = "SEC1234567890abcdef";

    private HttpServer server;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        // 端口 0 让 OS 分配空闲端口，避免冲突
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/robot/send", new Recorder());
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (Objects.nonNull(server)) {
            server.stop(0);
        }
    }

    @Test
    void sendMarkdownShouldPostValidSignedRequest() throws Exception {
        // 客户端指向 mock server，accessToken 由测试自行提供
        DingTalkClient client = new DingTalkClient("access_token_test", SECRET,
                "http://127.0.0.1:" + port + "/robot/send?access_token=");
        client.sendMarkdown("{\"msgtype\":\"markdown\",\"markdown\":{\"title\":\"t\",\"text\":\"hello\"}}");
        // POST 是同步的，等待异步 server 端 handler 落完
        Thread.sleep(100);

        // 取回 mock server 记录的最新请求
        String method = Recorder.lastMethod;
        String query = Recorder.lastQuery;
        String body = Recorder.lastBody;

        assertThat(method).isEqualTo("POST");
        assertThat(body)
                .contains("\"msgtype\":\"markdown\"")
                .contains("\"text\":\"hello\"");

        assertThat(query)
                .contains("access_token=access_token_test")
                .contains("timestamp=")
                .contains("&sign=");

        Map<String, String> q = parseQuery(query);
        long ts = Long.parseLong(q.get("timestamp"));
        // 客户端写入 URL 时做了 URLEncoder.encode（b64 中的 '+' 不编码、'=' 编码为 %3D）。
        // 我们在服务端解析时已经 URLDecoder 一次恢复为裸 b64，所以期望签名同样做"先 URL 编再解"。
        String expected = java.net.URLDecoder.decode(expectedSign(ts, SECRET), "UTF-8");
        assertThat(q.get("sign")).isEqualTo(expected);
    }

    /**
     * 期望签名（与 {@code DingTalkClient#getSign} 等价）。
     */
    private static String expectedSign(long timestamp, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal((timestamp + "\n" + secret).getBytes(StandardCharsets.UTF_8));
        String b64 = java.util.Base64.getEncoder().encodeToString(signData);
        return java.net.URLEncoder.encode(b64, "UTF-8");
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> out = new HashMap<>();
        if (Objects.isNull(query)) {
            return out;
        }
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                out.put(URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
            }
        }
        return out;
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
