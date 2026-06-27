package io.ddd4j.core.api.contract;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * MQ事件基类（纯 Java，无框架依赖）。
 * <p>
 * 支持多种发布模式：
 * <ul>
 *   <li>{@link PublishMode#MQ} - 只发布到 MQ Broker（默认）</li>
 *   <li>{@link PublishMode#LOCAL_EVENT} - 只发布到本地事件（框架适配层实现）</li>
 *   <li>{@link PublishMode#BOTH} - 同时发布到 MQ 和本地事件</li>
 * </ul>
 */
@Data
public class MQEvent implements Serializable {

    // 消息ID，默认当前时间戳
    protected String msgId;
    // 命名空间
    private String namespace;
    // 主题，配置 ddd4j.mq.default-topic 后无须每次指定
    protected String topic;
    // 标签，只支持单个标签，多标签需要分开发送
    protected String tag;
    // namespace、topic、tag拼接符
    protected String concat;
    // 租户ID，默认从线程上下文获取（外部系统 JSON 常用 tenant_id）
    @JsonAlias("tenant_id")
    protected String tenantId;

    // 发布模式：MQ | LOCAL_EVENT | BOTH
    private PublishMode publishMode = PublishMode.MQ;

    /**
     * 发布模式枚举。
     */
    public enum PublishMode {
        /** 只发布到 MQ Broker（默认，保持向后兼容） */
        MQ,
        /** 只发布到本地事件（框架适配层实现） */
        LOCAL_EVENT,
        /** 同时发布到 MQ 和本地事件（混合模式） */
        BOTH
    }

    // 策略匹配，supports参数来源于@MQEventListener.supports
    public boolean supports(List<String> supports) {
        return supports.contains(match());
    }

    // 策略匹配项
    public String match() {
        return "*";
    }

    /**
     * 设置发布模式（链式调用）。
     *
     * @param publishMode 发布模式
     * @return this
     */
    @SuppressWarnings("unchecked")
    public <T extends MQEvent> T publishMode(PublishMode publishMode) {
        this.publishMode = publishMode;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public <T extends MQEvent> T tenantId(String tenantId) {
        this.tenantId = tenantId;
        return (T) this;
    }

    /**
     * 获取默认主题（由框架适配层提供）
     *
     * @return 默认主题名
     */
    public String getDefaultTopic() {
        return "DEFAULT";
    }
}
