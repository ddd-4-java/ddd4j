package io.ddd4j.core.contract;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.ddd4j.core.context.SpringContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.contract.constant.ContextConstants;
import lombok.Data;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * MQ事件基类。
 * <p>
 * 支持多种发布模式：
 * <ul>
 *   <li>{@link PublishMode#MQ} - 只发布到 MQ Broker（默认）</li>
 *   <li>{@link PublishMode#SPRING_EVENT} - 只发布到 Spring ApplicationEvent</li>
 *   <li>{@link PublishMode#BOTH} - 同时发布到 MQ 和 Spring Event</li>
 * </ul>
 */
@Data
public class MQEvent implements Serializable, ApplicationEventPublisherAware {

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

    // Spring ApplicationEventPublisher（由 Spring 自动注入）
    private transient ApplicationEventPublisher applicationEventPublisher;

    // 发布模式：MQ | SPRING_EVENT | BOTH
    private PublishMode publishMode = PublishMode.MQ;

    /**
     * 发布模式枚举。
     */
    public enum PublishMode {
        /** 只发布到 MQ Broker（默认，保持向后兼容） */
        MQ,
        /** 只发布到 Spring ApplicationEvent（本地事件） */
        SPRING_EVENT,
        /** 同时发布到 MQ 和 Spring Event（混合模式） */
        BOTH
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
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
            setTopic(SpringContext.getEnv().getProperty("ddd4j.mq.default-topic", "DEFAULT"));
        }
        setTag(tag);
        setTenantId(tenantId != null ? tenantId : ThreadContext.get(ContextConstants.TENANT_ID));
        setMsgId(getMsgId() == null ? String.valueOf(System.currentTimeMillis()) : getMsgId());

        // 根据发布模式自动决定推送方式
        switch (publishMode) {
            case MQ:
                publishViaMQ();
                break;
            case SPRING_EVENT:
                publishViaSpringEvent();
                break;
            case BOTH:
                publishViaMQ();
                publishViaSpringEvent();
                break;
        }
    }

    /**
     * 通过 MQ 发布（现有逻辑）。
     */
    private void publishViaMQ() {
        if (!publishViaMQEventPublisher()) {
            throw new IllegalStateException(
                    "MQEventPublisher bean not found; enable ddd4j.mq and add a ddd4j-mq-* module");
        }
    }

    /**
     * 通过 Spring ApplicationEvent 发布。
     */
    private void publishViaSpringEvent() {
        if (applicationEventPublisher != null) {
            applicationEventPublisher.publishEvent(this);
        }
    }

    /**
     * 通过 Spring 容器发布（{@link MQEventPublisher} Bean）。
     * <p>
     * 支持多个 MQEventPublisher 实现，将事件推送到所有已注册的消息平台。
     *
     * @return 是否已成功发布（至少一个 Publisher 成功）
     */
    private boolean publishViaMQEventPublisher() {
        try {
            ApplicationContext context = SpringContext.getApplicationContext();
            if (context == null) {
                return false;
            }
            Map<String, MQEventPublisher> publishers = context.getBeansOfType(MQEventPublisher.class);
            if (publishers.isEmpty()) {
                return false;
            }
            // 遍历所有 MQEventPublisher，将事件推送到所有已注册的消息平台
            boolean published = false;
            for (MQEventPublisher publisher : publishers.values()) {
                try {
                    publisher.publish(this);
                    published = true;
                } catch (Exception e) {
                    // 单个 Publisher 失败不影响其他 Publisher
                    // 可以考虑记录日志或收集异常
                }
            }
            return published;
        } catch (Exception ignored) {
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
}
