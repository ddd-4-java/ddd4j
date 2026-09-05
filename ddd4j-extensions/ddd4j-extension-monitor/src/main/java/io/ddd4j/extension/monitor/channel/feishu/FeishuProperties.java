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

import lombok.Data;

/**
 * 飞书自定义机器人配置属性。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class FeishuProperties {

    /**
     * 是否启用
     */
    private boolean enable = true;
    /**
     * 飞书机器人 webhook 完整地址（含 hook token），形如：
     * {@code https://open.feishu.cn/open-apis/bot/v2/hook/<token>}
     */
    private String webhookUrl = "";
    /**
     * 加签密钥（"不勾选签名校验"时留空）
     */
    private String secret = "";
}
