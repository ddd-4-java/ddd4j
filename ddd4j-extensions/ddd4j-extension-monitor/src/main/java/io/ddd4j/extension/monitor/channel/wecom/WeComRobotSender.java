package io.ddd4j.extension.monitor.channel.wecom;

import io.ddd4j.extension.monitor.core.Sender;

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
