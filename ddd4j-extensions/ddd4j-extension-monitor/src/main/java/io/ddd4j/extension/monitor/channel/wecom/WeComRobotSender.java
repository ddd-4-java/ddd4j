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

import io.ddd4j.extension.monitor.Sender;

/**
 * 企业微信群机器人 {@link Sender} 适配器。was {@code QiWeiRobotSender}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class WeComRobotSender implements Sender {

    /** \u5c06\u6d88\u606f\u8bf7\u6c42\u53d1\u9001\u5230\u4f01\u4e1a\u5fae\u4fe1\u673a\u5668\u4eba\u7684 HTTP \u5ba2\u6237\u7aef\u3002 */
    private final WeComClient client;

    /**
     * @param key 企业微信机器人 Webhook key
     */
    public WeComRobotSender(String key) {
        this.client = new WeComClient(key);
    }

    /**
     * 暴露底层 HTTP 客户端，便于高级场景直接复用。
     *
     * @return 企业微信 HTTP 客户端
     */
    public WeComClient getClient() {
        return client;
    }

    /**
     * 通过当前适配器发出请求或消息。
     *
     * @param msg 已格式化为通道要求的字符串（通常为 markdown 或 JSON payload）
     */
    @Override
    public void send(String msg) {
        client.sendMarkdown(msg);
    }
}
