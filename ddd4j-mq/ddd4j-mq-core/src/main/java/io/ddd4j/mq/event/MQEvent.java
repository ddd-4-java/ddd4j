package io.ddd4j.mq.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.ddd4j.core.context.Contexts;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.mq.message.Destination;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * MQ 事件基类（纯 Java，零 Spring 依赖）。
 *
 * <p>业务事件继承本类后，通过 {@link #publish()} 发布到 MQ Broker。
 * 仅走 MQ 通道，进程内事件请使用 {@link io.ddd4j.core.ddd.event.DomainEvent}。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * public class OrderCreatedEvent extends MQEvent {
 *     private String orderId;
 *     private BigDecimal amount;
 *
 *     public OrderCreatedEvent() {}
 *     public OrderCreatedEvent(String orderId, BigDecimal amount) {
 *         this.orderId = orderId;
 *         this.amount = amount;
 *     }
 * }
 *
 * // 发布到 MQ
 * new OrderCreatedEvent("OBS-001", BigDecimal.valueOf(99.9)).publish();
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
     * 主题，为空时使用当前对象 topic。
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
     * 发布事件到 MQ Broker。
     *
     * <p>空值补齐规则：
     * <ul>
     *   <li>{@code topic} ← 参数 → 当前对象 topic（为空时由 BrokerAdapter 决定默认）</li>
     *   <li>{@code tenantId} ← 参数 → {@link ThreadContext} 租户上下文</li>
     *   <li>{@code msgId} ← 已有值 → 当前时间戳</li>
     * </ul>
     *
     * @param topic    主题
     * @param tag      标签
     * @param tenantId 租户 ID
     */
    public void publish(String topic, String tag, String tenantId) {
        if (Objects.nonNull(topic)) {
            this.setTopic(topic);
        }
        this.setTag(tag);
        this.setTenantId(Objects.nonNull(tenantId) ? tenantId : ThreadContext.get(ContextConstants.TENANT_ID));
        this.setMsgId(Objects.isNull(getMsgId()) ? String.valueOf(System.currentTimeMillis()) : getMsgId());

        MQEventPublisher publisher = Contexts.injectOrThrow(SpiKeys.MQ_EVENT_PUBLISHER, MQEventPublisher.class);
        publisher.publish(this, Destination.from(this));
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
}
