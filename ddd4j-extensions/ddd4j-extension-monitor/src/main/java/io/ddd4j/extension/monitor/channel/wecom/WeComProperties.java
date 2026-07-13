package io.ddd4j.extension.monitor.channel.wecom;

import lombok.Data;

/**
 * 企业微信群机器人配置属性。was {@code QiWeiProperties} 命名约定。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class WeComProperties {

    /**
     * 是否启用
     */
    private boolean enable = true;

    /**
     * 企业微信机器人 Webhook key
     */
    private String key = "";
}
