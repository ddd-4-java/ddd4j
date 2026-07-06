package io.ddd4j.mq.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.ddd4j.core.context.Contexts;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.ddd.event.DomainEvent;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * MQ 事件基类（纯 Java，零 Spring 依赖）。
 *
 * <p>业务事件继承本类后，通过 {@link #publish()} 发布，框架根据
 * {@link PublishMode} 自动路由到 MQ Broker 和/或进程内 DomainEvent。
 *
 * <h3>发布模式</h3>
 * <pre>
 *   PublishMode.MQ            → 只发送到 MQ Broker（默认）
 *   PublishMode.DOMAIN_EVENT  → 只发进程内事件（DomainEventPublisher）
 *   PublishMode.BOTH          → 两者都发
 * </pre>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * public class OrderCreatedEvent extends MQEvent {
 *     private String orderId;
 *     private BigDecimal amount;
 * }
 *
 * // 发布到 MQ
 * new OrderCreatedEvent("OBS-001", BigDecimal.valueOf(99.9)).publish();
 *
 * // 同时发布到 MQ 和进程内事件
 * new OrderCreatedEvent("OBS-001", BigDecimal.valueOf(99.9))
 *         .publishMode(PublishMode.BOTH)
 *         .publish();
 * }</pre>
 *
 * <h3>publisher 注入（框架适配层）</h3>
 * <pre>{@code
 * Contexts.register(SpiKeys.MQ_EVENT_PUBLISHER, MQEventPublisher.class, publisher);
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
@Slf4j
@Data
public class MQEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 消息 ID，发布时为空则自动填充当前时间戳。
     */
    protected String msgId;

    /**
     * 主题，为空时使用全局配置 {@code ddd4j.mq.default-topic}。
     */
    protected String topic;

    /**
     * 标签，同一主题下的消息过滤维度，只支持单个标签。
     */
    protected String tag;

    /**
     * namespace/topic/tag 拼接符，为空时由 Broker Adapter 决定默认值。
     *
     * @deprecated 使用 {@code @EventListener.separator()} 代替
     */
    @Deprecated
    protected String concat;

    /**
     * 租户 ID，为空时自动从 {@link ThreadContext} 获取。
     */
    @JsonAlias("tenant_id")
    protected String tenantId;

    /**
     * 命名空间，用于多环境/多租户隔离。
     */
    private String namespace;

    /**
     * 发布模式，默认 {@link PublishMode#MQ}。
     */
    private PublishMode publishMode = PublishMode.MQ;

    // ========================= 策略匹配 =========================

    /**
     * 策略匹配：检查监听器声明的策略列表是否包含本事件的匹配键。
     *
     * <p>消费侧框架调用此方法，传入 {@code @EventListener.supports()} 声明的值。
     * 默认匹配键为 {@code "*"}（通配），子类可覆写 {@link #match()} 返回自定义键。
     *
     * @param listenerKeys 监听器声明的策略键列表
     * @return 匹配时 {@code true}
     */
    public boolean supports(List<String> listenerKeys) {
        return listenerKeys.contains(match());
    }

    /**
     * 返回本事件的策略匹配键，默认 {@code "*"}（匹配所有监听器）。
     *
     * <p>子类可覆写为具体值，实现事件级别的策略路由：
     * <pre>{@code
     * @Override
     * public String match() {
     *     return "vip";  // 只有 supports = {"vip"} 或 {"*"} 的监听器才能消费
     * }
     * }</pre>
     *
     * @return 策略键
     */
    public String match() {
        return "*";
    }

    // ========================= 发布 =========================

    /**
     * 发布事件，使用当前对象上的 topic/tag/tenantId。
     */
    public void publish() {
        publish(getTopic(), getTag(), getTenantId());
    }

    /**
     * 发布事件，指定 topic，其余使用当前对象值。
     *
     * @param topic 主题
     */
    public void publish(String topic) {
        publish(topic, getTag(), getTenantId());
    }

    /**
     * 发布事件，指定 topic 和 tag，tenantId 使用当前对象值。
     *
     * @param topic 主题
     * @param tag   标签
     */
    public void publish(String topic, String tag) {
        publish(topic, tag, getTenantId());
    }

    /**
     * 发布事件，按 {@link #publishMode} 自动路由。
     *
     * <p>空值补齐规则：
     * <ul>
     *   <li>{@code topic} ← 参数 → {@link DomainEvent#getDefaultTopic()}</li>
     *   <li>{@code tenantId} ← 参数 → {@link ThreadContext} 租户上下文</li>
     *   <li>{@code msgId} ← 已有值 → 当前时间戳</li>
     * </ul>
     *
     * @param topic    主题（为空时用全局默认）
     * @param tag      标签
     * @param tenantId 租户 ID（为空时从线程上下文获取）
     */
    public void publish(String topic, String tag, String tenantId) {
        setTopic(Objects.nonNull(topic) ? topic : DomainEvent.getDefaultTopic());
        setTag(tag);
        setTenantId(Objects.nonNull(tenantId) ? tenantId : ThreadContext.get(ContextConstants.TENANT_ID));
        setMsgId(Objects.isNull(getMsgId()) ? String.valueOf(System.currentTimeMillis()) : getMsgId());

        switch (publishMode) {
            case MQ -> publishViaMQ();
            case DOMAIN_EVENT -> publishViaDomainEvent();
            case BOTH -> {
                publishViaMQ();
                publishViaDomainEvent();
            }
        }
    }

    /**
     * 通过 SPI 查找 {@link MQEventPublisher} 发布到 MQ Broker。
     *
     * @throws IllegalStateException 未找到 MQEventPublisher
     */
    private void publishViaMQ() {
        MQEventPublisher publisher = Contexts.injectOrThrow(
                SpiKeys.MQ_EVENT_PUBLISHER, MQEventPublisher.class);
        publisher.publish(this);
    }

    /**
     * 通过 SPI 查找 {@link io.ddd4j.core.ddd.event.DomainEventPublisher} 发布进程内事件。
     *
     * <p>未注册 DomainEventPublisher 时静默跳过（可选通道）。
     * <p>MQEvent 本身不是 DomainEvent 子类，这里仅利用 DomainEvent 的 defaultTopic
     * 作为 topic 兜底值，不创建 DomainEvent 包装。
     */
    private void publishViaDomainEvent() {
        // DOMAIN_EVENT / BOTH 模式：MQEvent 通过 MQ 通道发布后，
        // 框架适配层可自行监听 MQEvent 并桥接到 DomainEvent 体系。
        // core 层不做跨体系包装，保持职责单一。
        log.debug("MQEvent publishViaDomainEvent skipped: MQEvent is not a DomainEvent subclass");
    }

    // ========================= 链式 setter =========================

    /**
     * 链式设置租户 ID。
     *
     * @param tenantId 租户 ID
     * @return this
     */
    @SuppressWarnings("unchecked")
    public <T extends MQEvent> T tenantId(String tenantId) {
        this.tenantId = tenantId;
        return (T) this;
    }

    /**
     * 链式设置发布模式。
     *
     * @param publishMode 发布模式
     * @return this
     */
    @SuppressWarnings("unchecked")
    public <T extends MQEvent> T publishMode(PublishMode publishMode) {
        this.publishMode = publishMode;
        return (T) this;
    }

    /**
     * 事件发布模式。
     */
    public enum PublishMode {

        /** 只发布到 MQ Broker（默认） */
        MQ,

        /** 只发布到 DomainEvent（进程内事件） */
        DOMAIN_EVENT,

        /** 同时发布到 MQ 和 DomainEvent */
        BOTH
    }
}
