package io.ddd4j.extension.monitor.core;

import io.ddd4j.extension.monitor.channel.dingtalk.DingTalkRobotSender;
import io.ddd4j.extension.monitor.channel.feishu.FeishuRobotSender;
import io.ddd4j.extension.monitor.channel.wecom.WeComRobotSender;
import io.ddd4j.extension.monitor.runtime.ApplicationStartReporter;

/**
 * ddd4j 监控告警统一门面。
 *
 * <p>工具库对外的唯一入口，提供钉钉 / 企业微信两类机器人通道的快速装配，
 * 以及应用启动通告的便捷触发。
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 *   // 钉钉机器人
 *   DingTalkRobotSender sender = Monitor.ofDingTalk("access_token", "secret");
 *   sender.send("Hello, DingTalk");
 *
 *   // 企业微信机器人
 *   WeComRobotSender wecom = Monitor.ofWeCom("webhook_key");
 *   wecom.send("Hello, WeCom");
 *
 *   // 飞书机器人
 *   FeishuRobotSender feishu = Monitor.ofFeishu(
 *           "https://open.feishu.cn/open-apis/bot/v2/hook/xxx", "SECxxx");
 *   feishu.send("Hello, Feishu");
 *
 *   // 启动通告
 *   Monitor.startupReporter(sender, "my-app").init();
 * }</pre>
 *
 * <p>v2.x：从 {@code BaseMonitorConfig} 工厂集合迁移而来，对调用方提供更紧凑的入口；
 * 老式基于 {@link io.ddd4j.extension.monitor.config.BaseMonitorConfig} 的工厂方法仍然保留，
 * 用于从 {@link io.ddd4j.extension.monitor.config.BaseMonitorProperties} 装配。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class Monitor {

    private Monitor() {
    }

    /**
     * 创建钉钉群机器人发送器。
     *
     * @param accessToken 钉钉机器人 access_token
     * @param secret      钉钉机器人加签密钥
     * @return {@link DingTalkRobotSender}
     */
    public static DingTalkRobotSender ofDingTalk(String accessToken, String secret) {
        return new DingTalkRobotSender(accessToken, secret);
    }

    /**
     * 创建企业微信群机器人发送器。
     *
     * @param key 企业微信机器人 Webhook key
     * @return {@link WeComRobotSender}
     */
    public static WeComRobotSender ofWeCom(String key) {
        return new WeComRobotSender(key);
    }

    /**
     * 创建飞书群机器人发送器。
     *
     * @param webhookUrl 飞书机器人 webhook 完整地址（含 hook token）
     * @param secret      加签密钥（无则置 null 或空字符串）
     * @return {@link FeishuRobotSender}
     */
    public static FeishuRobotSender ofFeishu(String webhookUrl, String secret) {
        return new FeishuRobotSender(webhookUrl, secret);
    }

    /**
     * 创建应用启动通告器。
     *
     * @param sender  消息发送通道
     * @param appName 应用名称
     * @return {@link ApplicationStartReporter}
     */
    public static ApplicationStartReporter startupReporter(Sender sender, String appName) {
        return new ApplicationStartReporter(sender, appName);
    }
}
