package io.ddd4j.extension.monitor.channel.wecom;

import io.ddd4j.extension.monitor.Sender;

/**
 * 企业微信群机器人 {@link Sender} 适配器。was {@code QiWeiRobotSender}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class WeComRobotSender implements Sender {

    /** \u5c06\u6d88\u606f\u8bf7\u6c42\u53d1\u9001\u5230\u4f01\u4e1a\u5fae\u4fe1\u673a\u5668\u4eba\u7684 HTTP \u5ba2\u6237\u7aef\u3002 */
    private final WeComClient client;

    /**
     * 初始化 <code>WeComRobotSender</code> 使用的 <code>client</code>。
     *
     * @param key \u4f01\u4e1a\u5fae\u4fe1\u673a\u5668\u4eba Webhook key
     */
    public WeComRobotSender(String key) {
        this.client = new WeComClient(key);
    }

    /**
     * \u66b4\u9732\u5e95\u5c42 HTTP \u5ba2\u6237\u7aef\uff0c\u4fbf\u4e8e\u9ad8\u7ea7\u573a\u666f\u76f4\u63a5\u590d\u7528\u3002
     *
     * @return \u4f01\u4e1a\u5fae\u4fe1 HTTP \u5ba2\u6237\u7aef
     */
    public WeComClient getClient() {
        return client;
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
