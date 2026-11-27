/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    /** \u7b7e\u53d1\u548c\u6821\u9a8c\u6d4b\u8bd5\u4ee4\u724c\u56fa\u5b9a\u4f7f\u7528\u7684\u5bc6\u94a5\u6837\u672c\u3002 */
    private static final String SECRET = "SEC1234567890abcdef";
    /** \u5728\u6d4b\u8bd5\u7ebf\u7a0b\u4e4b\u95f4\u5171\u4eab\u5f53\u524d\u64cd\u4f5c\u6b21\u6570\u6216\u66f4\u65b0\u6570\u91cf\u7684\u8ba1\u6570\u5668\u3002 */
    private static final AtomicLong FROZEN_NOW = new AtomicLong();

    /** \u5f53\u524d\u6d4b\u8bd5\u542f\u52a8\u5e76\u8d1f\u8d23\u91ca\u653e\u7684\u672c\u5730\u670d\u52a1\u3002 */
    private HttpServer server;
    /** \u5f53\u524d\u6d4b\u8bd5\u670d\u52a1\u7ed1\u5b9a\u7684\u672c\u5730\u7aef\u53e3\u3002 */
    private int port;

    /**
     * \u8bfb\u53d6 <code>out</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param query \u7528\u6765\u8bfb\u53d6\u6d4b\u8bd5\u6570\u636e\u7684\u67e5\u8be2\u5bf9\u8c61
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>out</code>
     */
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
     * \u6d4b\u8bd5\u524d\u4f9d\u6b21\u542f\u52a8<code>server</code>\u3002
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/open-apis/bot/v2/hook", new Recorder());
        server.start();
        // 飞书 secret 用秒级时间戳，固定当前时间便于断言签名一致
        FROZEN_NOW.set(System.currentTimeMillis() / 1000L);
    }

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u505c\u6b62<code>server</code>\u3002 */
    @AfterEach
    void stopServer() {
        if (Objects.nonNull(server)) {
            server.stop(0);
        }
    }

    /**
     * \u9a8c\u8bc1<code>Recorder.lastMethod</code> \u7b49\u4e8e <code>"POST"</code>\uff0c<code>Recorder.lastQuery</code> \u5305\u542b <code>"timestamp="</code>\uff0c<code>q.get("sign")</code> \u7b49\u4e8e <code>expectedDecoded</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u3001\u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
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

    /**
     * \u9a8c\u8bc1<code>Recorder.lastMethod</code> \u7b49\u4e8e <code>"POST"</code>\uff0c<code>Recorder.lastQuery</code>\u4e3a\u7a7a\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u3001\u53d1\u8d77\u6d4b\u8bd5\u8bf7\u6c42\u65f6\u5931\u8d25
     */
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

    /** \u5b9e\u73b0 <code>HttpHandler</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>handle()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    static class Recorder implements HttpHandler {
        /** \u6d4b\u8bd5\u670d\u52a1\u5668\u6700\u8fd1\u6536\u5230\u7684 HTTP \u65b9\u6cd5\u3002 */
        static volatile String lastMethod;
        /** \u6d4b\u8bd5\u670d\u52a1\u5668\u6700\u8fd1\u6536\u5230\u7684\u8bf7\u6c42\u67e5\u8be2\u5b57\u7b26\u4e32\u3002 */
        static volatile String lastQuery;
        /** \u6d4b\u8bd5\u670d\u52a1\u5668\u6700\u8fd1\u6536\u5230\u7684 HTTP \u8bf7\u6c42\u6b63\u6587\u3002 */
        static volatile String lastBody;

        /**
         * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>lastMethod = exchange.getRequestMethod()</code>\u3001<code>lastQuery = uri.getRawQuery()</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
         * @param exchange \u5f53\u524d HTTP \u8bf7\u6c42\u53ca\u54cd\u5e94\u4ea4\u6362\u5bf9\u8c61
         * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            lastMethod = exchange.getRequestMethod();
            URI uri = exchange.getRequestURI();
            lastQuery = uri.getRawQuery();
            try (InputStream is = exchange.getRequestBody()) {
                lastBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            byte[] resp = "{\"StatusCode\":0,\"StatusMessage\":\"success\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        }
    }
}
