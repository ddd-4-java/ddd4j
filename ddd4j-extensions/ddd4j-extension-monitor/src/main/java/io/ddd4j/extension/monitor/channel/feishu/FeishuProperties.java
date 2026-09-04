package io.ddd4j.extension.monitor.channel.feishu;

import lombok.Data;

/**
 * 飞书自定义机器人配置属性。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
public class FeishuProperties {

    /**
     * 是否启用
     */
    private boolean enable = true;
    /**
     * 飞书机器人 webhook 完整地址（含 hook token），形如：
     * {@code https://open.feishu.cn/open-apis/bot/v2/hook/<token>}
     */
    private String webhookUrl = "";
    /**
     * 加签密钥（"不勾选签名校验"时留空）
     */
    private String secret = "";
}
