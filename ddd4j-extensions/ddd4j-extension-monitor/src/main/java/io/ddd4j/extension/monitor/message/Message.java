package io.ddd4j.extension.monitor.message;

import lombok.Data;

import java.util.List;

/**
 * 钉钉 / 企微通用消息体外层。was {@code MsgVO}。
 *
 * <p>序列化后的 JSON 与钉钉 / 企微 webhook 协议兼容。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class Message {

    /**
     * 消息类型：{@code text} / {@code markdown}
     */
    private String msgtype;

    /**
     * 文本消息体（{@code msgtype=text} 时使用）
     */
    private Text text;

    /**
     * {@code @} 配置
     */
    private At at;

    /**
     * Markdown 消息体（{@code msgtype=markdown} 时使用）
     */
    private Markdown markdown;

    /**
     * 构造一条 markdown 消息（带 @ 配置）。
     *
     * <p>同时填充 {@code text}（钉钉）与 {@code content}（企微）字段，两端兼容。
     *
     * @param title     标题
     * @param text      正文
     * @param atMobiles 被 @ 的手机号列表（null 表示不 @ 任何人）
     */
    public static Message markdown(String title, String text, List<String> atMobiles) {
        Message m = new Message();
        m.setMsgtype("markdown");
        m.setMarkdown(new Markdown(title, text, text));
        m.setAt(atMobiles == null || atMobiles.isEmpty() ? At.none() : At.ofMobiles(atMobiles));
        return m;
    }

    /**
     * 构造一条纯文本消息。
     */
    public static Message text(String content) {
        Message m = new Message();
        m.setMsgtype("text");
        m.setText(new Text());
        m.getText().setContent(content);
        return m;
    }
}
