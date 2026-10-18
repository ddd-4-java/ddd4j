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

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the shared Feishu post-message contract across the three JDK lines. */
class FeishuRobotSenderContractTest {

    @Test
    void sendsMarkdownAsPostParagraph() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            try (InputStream input = exchange.getRequestBody();
                 ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[1024];
                int count;
                while ((count = input.read(chunk)) != -1) {
                    bytes.write(chunk, 0, count);
                }
                body.set(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            }
            byte[] response = "{\"StatusCode\":0}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            FeishuRobotSender sender = new FeishuRobotSender(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/hook", "");
            sender.send("**hello**");

            assertThat(body.get()).contains("\"msg_type\":\"post\"")
                    .contains("\"tag\":\"text\"")
                    .contains("\"text\":\"**hello**\"");
        } finally {
            server.stop(0);
        }
    }
}
