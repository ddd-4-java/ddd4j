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

    private final WeComClient client;

    /**
     * @param key 企业微信机器人 Webhook key
     */
    public WeComRobotSender(String key) {
        this.client = new WeComClient(key);
    }

    /**
     * 暴露底层 HTTP 客户端，便于高级场景直接复用。
     */
    public WeComClient getClient() {
        return client;
    }

    @Override
    public void send(String msg) {
        client.sendMarkdown(msg);
    }
}
