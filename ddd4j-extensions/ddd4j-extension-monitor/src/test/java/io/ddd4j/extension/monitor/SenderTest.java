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
