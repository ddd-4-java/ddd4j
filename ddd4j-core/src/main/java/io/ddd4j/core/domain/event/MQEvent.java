package io.ddd4j.core.domain.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.ddd4j.core.context.Contexts;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.constant.ContextConstants;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * MQ 事件基类（纯 Java，零 Spring 依赖）。
 * <p>
 * 支持多种发布模式：
 * <ul>
 *   <li>{@link PublishMode#MQ} - 只发布到 MQ Broker（默认）</li>
 *   <li>{@link PublishMode#DOMAIN_EVENT} - 只发布到 DomainEvent（本地事件）</li>
 *   <li>{@link PublishMode#BOTH} - 同时发布到 MQ 和 DomainEvent</li>
 * </ul>
 *
 * <h3>publisher 查找机制（3.0.0+）</h3>
 * <p>
 * 不再使用静态 {@code registerPublisher}，改为在调用时按 SPI 约定 key
 * 从上下文动态查找（{@link Contexts#service(String, Class)}）。
 * 框架适配层在启动期通过 {@link Contexts#register(String, Class, Object)}
 * 注入 publisher（约定 key 参见 {@link SpiKeys#MQ_EVENT_PUBLISHER}）。
 * <p>
 * 优势：
 * <ul>
 *   <li><b>生命周期解耦</b>：业务 {@code new MQEvent()} 时无需关心 publisher 是否就绪</li>
 *   <li><b>多租户隔离</b>：线程级 publisher 可覆盖全局默认（多租户不同 MQ 集群）</li>
 *   <li><b>测试友好</b>：单测时直接 {@code ThreadContext.inject(KEY, type, mockImpl)}</li>
 *   <li><b>零静态可变状态</b>：符合 ddd4j 框架无关纯净契约理念</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
@Slf4j
@Data
public class MQEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
        if (Objects.isNull(getTopic())) {
            setTopic(DomainEvent.getDefaultTopic());
        }
        setTag(tag);
        setTenantId(Objects.nonNull(tenantId) ? tenantId : ThreadContext.get(ContextConstants.TENANT_ID));
        setMsgId(Objects.isNull(getMsgId()) ? String.valueOf(System.currentTimeMillis()) : getMsgId());

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
     * 通过上下文查找的 MQEventPublisher 发布。
     * <p>
     * 查找顺序：{@link ThreadContext} → {@link BaseContext}。
     */
    private void publishViaMQ() {
        MQEventPublisher publisher = Contexts.injectOrThrow(
                SpiKeys.MQ_EVENT_PUBLISHER, MQEventPublisher.class);
        try {
            publisher.publish(this);
        } catch (Exception e) {
            log.warn("Failed to publish MQEvent via MQEventPublisher", e);
            throw e;
        }
    }

    /**
     * 通过上下文查找的 DomainEventPublisher 发布（纯 Java）。
     */
    private void publishViaDomainEvent() {
        Contexts.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class)
                .ifPresent(publisher -> publisher.publish(new MQEventAsDomainEvent(this)));
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

        public MQEventAsDomainEvent(MQEvent source) {
            super(source);
        }
    }
}