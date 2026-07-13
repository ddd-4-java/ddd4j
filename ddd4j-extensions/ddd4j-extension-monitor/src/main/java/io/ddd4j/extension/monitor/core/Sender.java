package io.ddd4j.extension.monitor.core;

import io.ddd4j.extension.monitor.message.Message;
import io.ddd4j.kit.lang.JsonKit;

import java.util.List;

/**
 * 消息发送器抽象接口。
 *
 * <p>由各消息通道（钉钉 / 企微 / 飞书等）的适配器实现，是 monitor 工具库对外的统一契约。
 *
 * <p>v2.x 在契约里新增了 {@link #sendMarkdown(String, String)} 无参默认方法 —— 业务侧可以无差别调用而不用关心通道差异：
 * <ul>
 *   <li>{@link #sendMarkdown(String, String)}：title + 正文 markdown，不关心通道差异</li>
 *   <li>{@link #sendMarkdown(String, String, List)}：title + 正文 + @ 手机号列表</li>
 * </ul>
 * 默认实现通过 {@link Message#markdown} 构造协议 DTO 并委托 {@link #send(String)}。
 * 各通道若希望走更高效的协议层（钉钉的 {@code at} 结构、企微的 {@code at.atMobiles}），可重写以利用通道专属结构。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface Sender {

    /**
     * 发送一条消息（具体内容格式由实现决定）。
     *
     * @param msg 已格式化为通道要求的字符串（通常为 markdown 或 JSON payload）
     */
    void send(String msg);

    /**
     * 发送一条 markdown 消息（不带 @）。
     *
     * <p>默认实现：
     * <ol>
     *   <li>构造一条 {@code msgtype=markdown} 的 {@link Message}，{@code at} 留空</li>
     *   <li>{@link JsonKit#toJson(Object)} 序列化为 JSON 字符串</li>
     *   <li>通过 {@link #send(String)} 发出</li>
     * </ol>
     *
     * @param title 标题（钉钉 / 企微 markdown 显示在第一行）
     * @param text  markdown 正文
     */
    default void sendMarkdown(String title, String text) {
        sendMarkdown(title, text, null);
    }

    /**
     * 发送一条带 {@code @} 通知的 markdown 消息。
     *
     * <p>默认实现回落到 {@link #send(String)}；通道子类可重写以利用结构化 {@code at} 字段。
     *
     * @param title     标题（钉钉 / 企微 markdown 显示在第一行）
     * @param markdown  markdown 正文
     * @param atMobiles 被 @ 的手机号列表（可 null / 空）
     */
    default void sendMarkdown(String title, String markdown, List<String> atMobiles) {
        Message m = Message.markdown(title, markdown, atMobiles);
        send(renderMessage(m));
    }

    /**
     * 将 {@link Message} 序列化为通道通用的 JSON 字符串。
     */
    static String renderMessage(Message message) {
        return JsonKit.toJson(message);
    }
}
