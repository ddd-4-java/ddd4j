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
package io.ddd4j.extension.monitor;

import io.ddd4j.extension.monitor.message.Message;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Sender} 默认方法契约测试。
 *
 * <p>验证 {@code default sendMarkdown(...)} 在各实现下都能正确序列化 {@link Message} 并
 * 通过 {@link Sender#renderMessage(Message)} 委托到 {@code send(String)}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class SenderTest {

    /** \u9a8c\u8bc1<code>json</code>\u975e\u7a7a\uff0c<code>json2</code> \u7b49\u4e8e <code>Sender.renderMessage(m)</code>\u3002 */
    @Test
    void sendMarkdownTitleAndTextShouldProduceValidJson() {
        AtomicReference<String> captured = new AtomicReference<>();
        Sender sender = captured::set;

        sender.sendMarkdown("测试标题", "**hello** markdown body");

        String json = captured.get();
        assertThat(json).isNotNull();
        // msgtype + markdown title + text + 缺省 At
        assertThat(json)
                .contains("\"msgtype\":\"markdown\"")
                .contains("\"title\":\"测试标题\"")
                .contains("\"text\":\"**hello** markdown body\"")
                .contains("\"isAtAll\":false");
        // round-trip：再调用一次 renderMessage 不会抛异常
        Message m = Message.markdown("r", "t", null);
        String json2 = Sender.renderMessage(m);
        assertThat(json2).isEqualTo(Sender.renderMessage(m));
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>mobiles.add()</code>\u3001<code>sender.sendMarkdown()</code>\u3001<code>captured.get()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void sendMarkdownWithAtMobilesShouldHonorList() {
        AtomicReference<String> captured = new AtomicReference<>();
        Sender sender = captured::set;

        List<String> mobiles = new ArrayList<>();
        mobiles.add("13800001111");
        mobiles.add("13800002222");
        sender.sendMarkdown("", "重要通知", mobiles);

        String json = captured.get();
        assertThat(json)
                .contains("\"msgtype\":\"markdown\"")
                .contains("\"at\":")
                .contains("\"atMobiles\":[\"13800001111\",\"13800002222\"]")
                .contains("\"text\":\"重要通知\"");
    }

    /** \u9a8c\u8bc1<code>json</code> \u5305\u542b <code>"\"at\":"</code>\u3002 */
    @Test
    void sendMarkdownWithEmptyAtMobilesShouldEmptiedAtField() {
        AtomicReference<String> captured = new AtomicReference<>();
        Sender sender = captured::set;

        sender.sendMarkdown("t", "x", new ArrayList<>());

        String json = captured.get();
        assertThat(json)
                .contains("\"at\":")
                .contains("\"isAtAll\":false")
                .doesNotContain("\"atMobiles\"");
    }

    /** \u9a8c\u8bc1<code>json</code> \u4ee5\u2026\u5f00\u5934 <code>"{"</code>\u3002 */
    @Test
    void renderMessageShouldBeJsonString() {
        Message m = Message.markdown("title", "body", null);
        String json = Sender.renderMessage(m);
        assertThat(json)
                .startsWith("{")
                .endsWith("}")
                .contains("\"msgtype\":\"markdown\"");
    }
}
