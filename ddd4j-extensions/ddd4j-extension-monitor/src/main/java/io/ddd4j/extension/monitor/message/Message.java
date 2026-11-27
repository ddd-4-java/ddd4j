package io.ddd4j.extension.monitor.message;

import io.ddd4j.kit.lang.CollKit;
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
     * \u6784\u9020\u4e00\u6761 markdown \u6d88\u606f\uff08\u5e26 @ \u914d\u7f6e\uff09\u3002
     *
     *  <p>\u540c\u65f6\u586b\u5145 {@code text}\uff08\u9489\u9489\uff09\u4e0e {@code content}\uff08\u4f01\u5fae\uff09\u5b57\u6bb5\uff0c\u4e24\u7aef\u517c\u5bb9\u3002
     * @param title \u6807\u9898
     * @param text \u6b63\u6587
     * @param atMobiles \u88ab @ \u7684\u624b\u673a\u53f7\u5217\u8868\uff08null \u8868\u793a\u4e0d @ \u4efb\u4f55\u4eba\uff09
     *
     * @return Markdown \u6d88\u606f
     */
    public static Message markdown(String title, String text, List<String> atMobiles) {
        Message m = new Message();
        m.setMsgtype("markdown");
        m.setMarkdown(new Markdown(title, text, text));
        m.setAt(CollKit.isEmpty(atMobiles) ? At.none() : At.ofMobiles(atMobiles));
        return m;
    }

    /**
     * \u6784\u9020\u4e00\u6761\u7eaf\u6587\u672c\u6d88\u606f\u3002
     *
     * @param content \u6587\u672c\u5185\u5bb9
     * @return \u7eaf\u6587\u672c\u6d88\u606f
     */
    public static Message text(String content) {
        Message m = new Message();
        m.setMsgtype("text");
        m.setText(new Text());
        m.getText().setContent(content);
        return m;
    }
}
