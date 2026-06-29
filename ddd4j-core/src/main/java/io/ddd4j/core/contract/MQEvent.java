package io.ddd4j.core.contract;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.contract.constant.ContextConstants;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.List;

/**
 * MQ事件基类（纯 Java，零 Spring 依赖）。
 * <p>
 * 支持多种发布模式：
 * <ul>
 *   <li>{@link PublishMode#MQ} - 只发布到 MQ Broker（默认）</li>
 *   <li>{@link PublishMode#DOMAIN_EVENT} - 只发布到 DomainEvent（本地事件）</li>
 *   <li>{@link PublishMode#BOTH} - 同时发布到 MQ 和 DomainEvent</li>
 * </ul>
 * <p>
 * 发布 MQ 时通过静态注册的 {@link MQEventPublisher} 实现。
 * 发布 DomainEvent 时通过 {@link DomainEvent#publish()} 委托。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@Data
public class MQEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 静态 MQEventPublisher 注册（由框架适配层注入）
     */
    private static volatile MQEventPublisher mqEventPublisher;

    // 消息ID，默认当前时间戳
    protected String msgId;
    // 主题，配置 ddd4j.mq.default-topic 后无须每次指定
    protected String topic;
    // 标签，只支持单个标签，多标签需要分开发送
    protected String tag;
    // namespace、topic、tag拼接符
    protected String concat;
    // 租户ID，默认从线程上下文获取（外部系统 JSON 常用 tenant_id）
    @JsonAlias("tenant_id")
    protected String tenantId;
    // 命名空间
    private String namespace;
    // 发布模式：MQ | DOMAIN_EVENT | BOTH
    private PublishMode publishMode = PublishMode.MQ;

    /**
     * 注册 MQEventPublisher（由框架适配层调用）
     */
    public static void registerPublisher(MQEventPublisher publisher) {
        mqEventPublisher = publisher;
    }

    /**
     * 获取当前注册的 MQEventPublisher
     */
    public static MQEventPublisher getPublisher() {
        return mqEventPublisher;
    }

    // 策略匹配，supports参数来源于@MQEventListener.supports
    public boolean supports(List<String> supports) {
        return supports.contains(match());
    }

    // 策略匹配项
    public String match() {
        return "*";
    }

    // 发布MQ事件
    public void publish() {
        this.publish(getTopic(), getTag(), getTenantId());
    }

    // 发布MQ事件
    public void publish(String topic) {
        this.publish(topic, getTag(), getTenantId());
    }

    // 发布MQ事件
    public void publish(String topic, String tag) {
        this.publish(topic, tag, getTenantId());
    }

    /**
     * 发布事件，根据 publishMode 自动决定推送方式。
     *
     * @param topic    主题
     * @param tag      标签
     * @param tenantId 租户 ID
     */
    public void publish(String topic, String tag, String tenantId) {
        setTopic(topic);
        if (getTopic() == null) {
            setTopic(DomainEvent.getDefaultTopic());
        }
        setTag(tag);
        setTenantId(tenantId != null ? tenantId : ThreadContext.get(ContextConstants.TENANT_ID));
        setMsgId(getMsgId() == null ? String.valueOf(System.currentTimeMillis()) : getMsgId());

        // 根据发布模式自动决定推送方式
        switch (publishMode) {
            case MQ:
                publishViaMQ();
                break;
            case DOMAIN_EVENT:
                publishViaDomainEvent();
                break;
            case BOTH:
                publishViaMQ();
                publishViaDomainEvent();
                break;
        }
    }

    /**
     * 通过 MQ 发布。
     */
    private void publishViaMQ() {
        if (!publishViaMQEventPublisher()) {
            throw new IllegalStateException(
                    "MQEventPublisher not registered; call MQEvent.registerPublisher() or use ddd4j-mq-* module");
        }
    }

    /**
     * 通过 DomainEventPublisher 发布（纯 Java）。
     */
    private void publishViaDomainEvent() {
        DomainEventPublisher publisher = DomainEvent.getPublisher();
        if (publisher != null) {
            publisher.publish(new MQEventAsDomainEvent(this));
        }
    }

    /**
     * 通过静态注册的 MQEventPublisher 发布。
     *
     * @return 是否已成功发布
     */
    private boolean publishViaMQEventPublisher() {
        if (mqEventPublisher == null) {
            return false;
        }
        try {
            mqEventPublisher.publish(this);
            return true;
        } catch (Exception e) {
            log.warn("Failed to publish MQEvent via MQEventPublisher", e);
            return false;
        }
    }

    public <T extends MQEvent> T tenantId(String tenantId) {
        this.tenantId = tenantId;
        return (T) this;
    }

    /**
     * 设置发布模式（链式调用）。
     *
     * @param publishMode 发布模式
     * @return this
     */
    public <T extends MQEvent> T publishMode(PublishMode publishMode) {
        this.publishMode = publishMode;
        return (T) this;
    }

    /**
     * 发布模式枚举。
     */
    public enum PublishMode {
        /**
         * 只发布到 MQ Broker（默认，保持向后兼容）
         */
        MQ,
        /**
         * 只发布到 DomainEvent（本地事件，通过 DomainEventPublisher）
         */
        DOMAIN_EVENT,
        /**
         * 同时发布到 MQ 和 Domain Event（混合模式）
         */
        BOTH
    }

    /**
     * 将 MQEvent 包装为 DomainEvent 的适配器
     */
    private static class MQEventAsDomainEvent extends DomainEvent<MQEvent> {
        private static final long serialVersionUID = 1L;

        public MQEventAsDomainEvent(MQEvent source) {
            super(source);
        }
    }
}
