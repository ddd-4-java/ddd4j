package io.ddd4j.extension.monitor.channel.dingtalk;

import lombok.Data;

/**
 * 钉钉群机器人配置属性。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class DingTalkProperties {

    /**
     * 是否启用
     */
    private boolean enable = true;

    /**
     * 钉钉机器人 Webhook access_token
     */
    private String token = "";

    /**
     * 钉钉机器人加签密钥
     */
    private String secret = "";
}
