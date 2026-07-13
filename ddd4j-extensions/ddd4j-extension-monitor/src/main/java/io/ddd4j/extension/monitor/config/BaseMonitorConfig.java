package io.ddd4j.extension.monitor.config;

import io.ddd4j.extension.monitor.core.Sender;
import io.ddd4j.extension.monitor.channel.dingtalk.DingTalkRobotSender;
import io.ddd4j.extension.monitor.channel.feishu.FeishuRobotSender;
import io.ddd4j.extension.monitor.channel.wecom.WeComRobotSender;
import io.ddd4j.extension.monitor.runtime.ApplicationStartReporter;

/**
 * ddd4j 监控告警配置：核心对象的纯 Java 工厂。
 *
 * <p>不再依赖 Spring 容器，仅提供各核心对象的工厂方法，
 * 由上层框架（Spring/Quarkus/Javalin）完成装配与生命周期管理。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class BaseMonitorConfig {

    public BaseMonitorProperties baseMonitorProperties() {
        return new BaseMonitorProperties();
    }

    /**
     * 创建钉钉机器人发送器。
     *
     * @param properties 监控配置
     * @return {@link DingTalkRobotSender} 实例
     */
    public DingTalkRobotSender dingTalkRobotSender(BaseMonitorProperties properties) {
        return new DingTalkRobotSender(
                properties.getLog().getDingtalk().getToken(),
                properties.getLog().getDingtalk().getSecret());
    }

    /**
     * 创建企业微信机器人发送器（was {@code qiWeiRobotSender}）。
     *
     * @param properties 监控配置
     * @return {@link WeComRobotSender} 实例
     */
    public WeComRobotSender wecomRobotSender(BaseMonitorProperties properties) {
        return new WeComRobotSender(properties.getLog().getWecom().getKey());
    }

    /**
     * 创建飞书机器人发送器（v2.x 新增）。
     *
     * @param properties 监控配置
     * @return {@link FeishuRobotSender} 实例
     */
    public FeishuRobotSender feishuRobotSender(BaseMonitorProperties properties) {
        return new FeishuRobotSender(
                properties.getLog().getFeishu().getWebhookUrl(),
                properties.getLog().getFeishu().getSecret());
    }

    /**
     * 创建应用启动通告器。
     *
     * @param sender  消息发送通道
     * @param appName 应用名称
     * @return {@link ApplicationStartReporter} 实例
     */
    public ApplicationStartReporter applicationStartReporter(Sender sender,
                                                             String appName) {
        return new ApplicationStartReporter(sender, appName);
    }
}
