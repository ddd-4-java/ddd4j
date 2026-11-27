package io.ddd4j.extension.monitor.channel.dingtalk;

import io.ddd4j.extension.monitor.Sender;
import lombok.Getter;

/**
 * 钉钉群机器人 {@link Sender} 适配器。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Getter
public class DingTalkRobotSender implements Sender {

    /**
     * 实际 HTTP 客户端暴露（仅高级场景使用，例如绕过签名直接调试）。
     */
    private final DingTalkClient client;

    /**
     * 初始化 <code>DingTalkRobotSender</code> 使用的 <code>client</code>。
     *
     * @param accessToken \u9489\u9489\u673a\u5668\u4eba access_token
     * @param secret \u9489\u9489\u673a\u5668\u4eba\u52a0\u7b7e\u5bc6\u94a5
     */
    public DingTalkRobotSender(String accessToken, String secret) {
        this.client = new DingTalkClient(accessToken, secret);
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
