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

    /** \u5f53\u524d\u6d4b\u8bd5\u542f\u52a8\u5e76\u8d1f\u8d23\u91ca\u653e\u7684\u672c\u5730\u670d\u52a1\u3002 */
    private HttpServer server;
    /** \u5f53\u524d\u6d4b\u8bd5\u670d\u52a1\u7ed1\u5b9a\u7684\u672c\u5730\u7aef\u53e3\u3002 */
    private int port;

    /**
     * \u6d4b\u8bd5\u524d\u4f9d\u6b21\u542f\u52a8<code>server</code>\u3002
     * @throws IOException \u8bfb\u53d6\u6216\u5199\u5165\u6d4b\u8bd5\u6570\u636e\u65f6\u53d1\u751f I/O \u9519\u8bef
     */
    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/cgi-bin/webhook/send", new Recorder());
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
     * \u9a8c\u8bc1<code>method</code> \u7b49\u4e8e <code>"POST"</code>\uff0c<code>query</code> \u7b49\u4e8e <code>"key=wehook_key_test"</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
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
