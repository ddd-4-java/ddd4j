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

import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.extension.monitor.message.Message;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 企业微信群机器人 Webhook 客户端。was {@code QiWeiService}。
 *
 * <p>纯 Java，使用 JDK {@link HttpClient} 推送 markdown 格式消息。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j(topic = "### DDD4j-MONITOR : WeComClient ###")
public class WeComClient {

    /**
     * 企业微信群机器人 Webhook 基础地址
     */
    public static final String BASE_URL = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=";

    /**
     * HTTP 客户端（连接超时 10s）
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 企业微信机器人 Webhook key
     */
    private final String key;
    /**
     * Webhook 基础地址（可被构造函数替换，常量 {@link #BASE_URL} 是默认值）
     */
    private final String baseUrl;

    /**
     * 默认构造函数，使用 {@link #BASE_URL}。
     */
    public WeComClient(String key) {
        this(key, BASE_URL);
    }

    /**
     * 可注入 baseUrl 的构造函数（用于测试或企业内代理场景）。
     */
    public WeComClient(String key, String baseUrl) {
        this.key = key;
        this.baseUrl = baseUrl;
    }

    /** @return 配置的企业微信 webhook key */
    public String key() {
        return key;
    }

    /** @return 当前生效的 webhook 基础地址（含 {@code ?key=}） */
    public String baseUrl() {
        return baseUrl;
    }

    /**
     * 发送 markdown 消息。
     *
     * <p>企业微信群机器人 markdown 消息体里 {@code title} 与 {@code content} 的差异由各自客户端决定；
     * 这里保持"传入即正文"的语义，title 取首行非空文本作为标题候选。
     *
     * @param msg 纯 markdown 文本（不带 JSON 协议层）
     */
    public void sendMarkdown(String msg) {
        // title 留空，避免与 content 不一致；content 字段才是企业微信实际渲染的正文
        Message content = Message.markdown("", msg, null);
        String payload = JsonKit.toJson(content);
        String url = baseUrl + key;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.debug("【发送企业微信告警】响应：{}", response.body());
        } catch (Exception e) {
            log.error("【发送企业微信告警】error: {}", e.getMessage(), e);
        }
    }
}
