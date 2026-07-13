package io.ddd4j.extension.monitor.channel.dingtalk;

import io.ddd4j.extension.monitor.core.Sender;
import lombok.Getter;

/**
 * 钉钉群机器人 {@link Sender} 适配器。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Getter
public class DingTalkRobotSender implements Sender {

    /**
     *  实际 HTTP 客户端暴露（仅高级场景使用，例如绕过签名直接调试）。
     */
    private final DingTalkClient client;

    /**
     * @param accessToken 钉钉机器人 access_token
     * @param secret      钉钉机器人加签密钥
     */
    public DingTalkRobotSender(String accessToken, String secret) {
        this.client = new DingTalkClient(accessToken, secret);
    }

    @Override
    public void send(String msg) {
        client.sendMarkdown(msg);
    }
}
