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
package io.ddd4j.extension.monitor.channel.dingtalk;

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

    /** \u7b7e\u53d1\u548c\u6821\u9a8c\u6d4b\u8bd5\u4ee4\u724c\u56fa\u5b9a\u4f7f\u7528\u7684\u5bc6\u94a5\u6837\u672c\u3002 */
    private static final String SECRET = "SEC1234567890abcdef";

    /** \u5f53\u524d\u6d4b\u8bd5\u542f\u52a8\u5e76\u8d1f\u8d23\u91ca\u653e\u7684\u672c\u5730\u670d\u52a1\u3002 */
    private HttpServer server;
    /** \u5f53\u524d\u6d4b\u8bd5\u670d\u52a1\u7ed1\u5b9a\u7684\u672c\u5730\u7aef\u53e3\u3002 */
    private int port;

    /**
     * \u671f\u671b\u7b7e\u540d\uff08\u4e0e {@code DingTalkClient#getSign} \u7b49\u4ef7\uff09\u3002
     *
     * @param timestamp \u7b7e\u540d\u4f7f\u7528\u7684\u65f6\u95f4\u6233
     * @param secret \u7528\u4e8e\u8ba1\u7b97\u7b7e\u540d\u7684\u5bc6\u94a5
     * @return \u7528\u4e8e\u6bd4\u8f83\u7684\u9884\u671f\u7b7e\u540d
     * @throws Exception \u6d4b\u8bd5\u7b7e\u540d\u7b97\u6cd5\u65e0\u6cd5\u521b\u5efa\u6216\u521d\u59cb\u5316
     */
    private static String expectedSign(long timestamp, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal((timestamp + "\n" + secret).getBytes(StandardCharsets.UTF_8));
        String b64 = java.util.Base64.getEncoder().encodeToString(signData);
        return java.net.URLEncoder.encode(b64, "UTF-8");
    }

    /**
     * \u8bfb\u53d6 <code>out</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param query \u7528\u6765\u8bfb\u53d6\u6d4b\u8bd5\u6570\u636e\u7684\u67e5\u8be2\u5bf9\u8c61
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>out</code>
     * @throws java.io.UnsupportedEncodingException \u5c1d\u8bd5\u6267\u884c\u5f53\u524d\u6d4b\u8bd5\u64cd\u4f5c\u5931\u8d25
     */
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

    /**
     * \u6d4b\u8bd5\u524d\u4f9d\u6b21\u542f\u52a8<code>server</code>\u3002
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
    @BeforeEach
    void startServer() throws IOException {
        // 端口 0 让 OS 分配空闲端口，避免冲突
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/robot/send", new Recorder());
        server.start();
    }

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u505c\u6b62<code>server</code>\u3002 */
    @AfterEach
    void stopServer() {
        if (Objects.nonNull(server)) {
            server.stop(0);
        }
    }

    /**
     * \u9a8c\u8bc1<code>method</code> \u7b49\u4e8e <code>"POST"</code>\uff0c<code>query</code> \u5305\u542b <code>"access_token=access_token_test"</code>\uff0c<code>q.get("sign")</code> \u7b49\u4e8e <code>expected</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
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
     * 简易 recorder，记录最近一次请求的 method / query / body。
     */
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
                java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = is.read(chunk)) != -1) {
                    buf.write(chunk, 0, n);
                }
                lastBody = new String(buf.toByteArray(), StandardCharsets.UTF_8);
            }
            byte[] resp = "{\"errcode\":0,\"errmsg\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        }
    }
}
