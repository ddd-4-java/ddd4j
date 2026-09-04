package io.ddd4j.extension.monitor.channel.feishu;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FeishuClient} 集成测试。
 *
 * <p>使用 JDK 自带 {@link HttpServer} mock 飞书 webhook，验证：
 * <ul>
 *   <li>POST 到目标 URL</li>
 *   <li>签名模式：query 中含 {@code timestamp} + {@code sign}，签名满足
 *       {@code Base64(HmacSHA256("timestamp\n" + secret))}</li>
 *   <li>无签名模式：query 不附带 timestamp/sign</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class FeishuClientTest {

    private static final String SECRET = "SEC1234567890abcdef";
    private static final AtomicLong FROZEN_NOW = new AtomicLong();

    private HttpServer server;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/open-apis/bot/v2/hook", new Recorder());
        server.start();
        // 飞书 secret 用秒级时间戳，固定当前时间便于断言签名一致
        FROZEN_NOW.set(System.currentTimeMillis() / 1000L);
    }

    @AfterEach
    void stopServer() {
        if (Objects.nonNull(server)) {
            server.stop(0);
        }
    }

    @Test
    void sendWithSecretShouldPostValidSignedRequest() throws Exception {
        FeishuClient client = new FeishuClient(
                "http://127.0.0.1:" + port + "/open-apis/bot/v2/hook/test-token", SECRET);
        Recorder.lastMethod = null;
        Recorder.lastQuery = null;
        Recorder.lastBody = null;

        // 用 freezeClock 模式：因为签名时间戳是 System.currentTimeMillis()/1000，
        // 校验时不依赖具体值，而是重新用 Server 收到的 timestamp 反算
        client.send("{\"msg_type\":\"text\",\"content\":{\"text\":\"hi\"}}");
        Thread.sleep(100);

        assertThat(Recorder.lastMethod).isEqualTo("POST");
        assertThat(Recorder.lastQuery).contains("timestamp=").contains("&sign=");

        // 解析 query，重新计算签名
        Map<String, String> q = parseQuery(Recorder.lastQuery);
        assertThat(q).containsKey("timestamp");
        assertThat(q).containsKey("sign");
        long ts = Long.parseLong(q.get("timestamp"));

        // 期望签名：客户端对 b64 做 URLEncoder.encode（'+' 不编码、'=' 编码为 %3D）。
        // mock 端的 Recorder 已经 URLDecoder.decode 恢复了裸 b64（'/' 变回 '/'，'%3D' 变回 '='）。
        // 因此我们再对期望值做一次 URLDecoder.decode，对齐二者。
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal((ts + "\n" + SECRET).getBytes(StandardCharsets.UTF_8));
        String expectedRaw = java.util.Base64.getEncoder().encodeToString(signData);
        String expectedEncoded = java.net.URLEncoder.encode(expectedRaw, "UTF-8");
        String expectedDecoded = java.net.URLDecoder.decode(expectedEncoded, "UTF-8");
        assertThat(q.get("sign")).isEqualTo(expectedDecoded);

        assertThat(Recorder.lastBody).contains("\"msg_type\":\"text\"");
    }

    @Test
    void sendWithoutSecretShouldPostPlainRequest() throws Exception {
        FeishuClient client = new FeishuClient(
                "http://127.0.0.1:" + port + "/open-apis/bot/v2/hook/test-token", "");
        Recorder.lastMethod = null;
        Recorder.lastQuery = null;
        Recorder.lastBody = null;

        client.send("{\"msg_type\":\"text\",\"content\":{\"text\":\"no-secret\"}}");
        Thread.sleep(100);

        assertThat(Recorder.lastMethod).isEqualTo("POST");
        // 无签名 → 无 query
        assertThat(Recorder.lastQuery).isNull();
        assertThat(Recorder.lastBody).contains("\"msg_type\":\"text\"");
    }

    private static Map<String, String> parseQuery(String query) throws java.io.UnsupportedEncodingException {
        Map<String, String> out = new HashMap<>();
        if (Objects.isNull(query)) {
            return out;
        }
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                out.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
                        URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
            }
        }
        return out;
    }

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
                java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = is.read(chunk)) != -1) {
                    buf.write(chunk, 0, n);
                }
                lastBody = new String(buf.toByteArray(), StandardCharsets.UTF_8);
            }
            byte[] resp = "{\"StatusCode\":0,\"StatusMessage\":\"success\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        }
    }
}
