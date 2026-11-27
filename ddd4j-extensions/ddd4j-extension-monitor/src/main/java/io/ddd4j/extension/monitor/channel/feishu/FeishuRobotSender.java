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

import io.ddd4j.extension.monitor.Sender;
import io.ddd4j.kit.lang.JsonKit;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 飞书群机器人 {@link Sender} 适配器。
 *
 * <p>{@link #send(String)} 默认走 {@code msg_type=post}（富文本，能近似渲染 markdown）；
 * 如需文本/消息卡片等更丰富的协议层，请调用 {@link FeishuClient#send(String)} 传入完整 JSON payload。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class FeishuRobotSender implements Sender {

    /** \u5c06\u6d88\u606f\u8bf7\u6c42\u53d1\u9001\u5230\u98de\u4e66\u81ea\u5b9a\u4e49\u673a\u5668\u4eba\u7684 HTTP \u5ba2\u6237\u7aef\u3002 */
    private final FeishuClient client;

    /**
     * 初始化 <code>FeishuRobotSender</code> 使用的 <code>client</code>。
     *
     * @param webhookUrl \u98de\u4e66\u673a\u5668\u4eba webhook \u5b8c\u6574\u5730\u5740\uff08\u542b hook token\uff09
     * @param secret \u52a0\u7b7e\u5bc6\u94a5\uff08\u65e0\u5219\u7f6e null \u6216\u7a7a\u5b57\u7b26\u4e32\uff09
     */
    public FeishuRobotSender(String webhookUrl, String secret) {
        this.client = new FeishuClient(webhookUrl, secret);
    }

    /**
     * \u66b4\u9732\u5e95\u5c42 HTTP \u5ba2\u6237\u7aef\uff0c\u4fbf\u4e8e\u9ad8\u7ea7\u573a\u666f\u76f4\u63a5\u590d\u7528\u3002
     *
     * @return \u98de\u4e66 HTTP \u5ba2\u6237\u7aef
     */
    public FeishuClient getClient() {
        return client;
    }

    /**
     * send() 方法。
     * @param msg 字符串参数。
     */
    @Override
    public void send(String msg) {
        // 入参 msg 是 markdown 文本，这里包一层为标准 post 富文本结构：
        //   post 协议要求 content[0] 是富文本二维数组 [[ {tag: text, text: msg} ]]。
        Map<String, Object> textPara = new HashMap<>();
        textPara.put("tag", "text");
        textPara.put("text", msg);
        List<List<Map<String, Object>>> paragraphs = List.of(List.of(textPara));

        Map<String, Object> zhCn = new HashMap<>();
        zhCn.put("title", "");
        zhCn.put("content", paragraphs);

        Map<String, Object> postNode = new HashMap<>();
        postNode.put("zh_cn", zhCn);

        Map<String, Object> root = new HashMap<>();
        root.put("msg_type", "post");
        root.put("content", postNode);

        client.send(JsonKit.toJson(root));
    }

    /**
     * 飞书 post 富文本消息 JSON 字符串构造辅助结构，仅本类使用。
     */
    @Data
    @NoArgsConstructor
    static class PostPayload {
        /** \u98de\u4e66\u5bcc\u6587\u672c\u6d88\u606f\u7684\u6b63\u6587\u7ed3\u6784\u3002 */
        private Map<String, Object> post;
    }
}
