package io.ddd4j.mq.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * MQ 事件基类（纯 Java，零 Spring 依赖）。
 *
 * <p>业务事件继承本类后，通过 {@link #publish()} 发布到 MQ Broker。
 * 仅走 MQ 通道，进程内事件请使用 {@code io.ddd4j.core.ddd.event.DomainEvent}。
 *
 * <p>发布机制（对齐 base-mq）：{@link #publish(String, String, String)} 通过
 * {@link BaseContext} 查找 key 为 {@link #MQ_EVENT_PUBLISHER} 的 {@link Consumer<MQEvent>}，
 * 由 {@link MQClient#initProducer(MQProperties)} 创建并注册。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * public class OrderCreatedEvent extends MQEvent {
 *     private String orderId;
 *     private BigDecimal amount;
 * }
 * new OrderCreatedEvent("OBS-001", BigDecimal.valueOf(99.9)).publish();
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
     * {@link BaseContext} key：MQ 事件发布者（{@link Consumer<MQEvent>}）。
     * 由 {@link MQClient} 在 {@code initProducer} 后注册。
     */
    public static final String MQ_EVENT_PUBLISHER = "MQEventPublisher";

    /**
     * {@link BaseContext} key：MQ 配置（{@link MQProperties}），
     * 用于获取 defaultTopic / namespace 默认值。
     */
    public static final String MQ_PROPERTIES = "MQProperties";

    /** 消息 ID，默认当前时间戳 */
    protected String msgId;
    /** 命名空间，配置 {@code ddd4j.mq.namespace} 后无须每次指定 */
    private String namespace;
    /** 主题，配置 {@code ddd4j.mq.default-topic} 后无须每次指定 */
    protected String topic;
    /** 标签，只支持单个标签，多标签需要分开发送 */
    protected String tag;
    /** namespace/topic/tag 拼接符，为空时由各 broker 实现决定默认值 */
    protected String concat;
    /** 租户 ID，默认从线程上下文获取（外部 JSON 常用 tenant_id） */
    @JsonAlias("tenant_id")
    protected String tenantId;

    /**
     * 策略匹配：supports 参数来源于 {@code @MQEventListener.supports}。
     */
    public boolean supports(List<String> supports) {
        return supports.contains(match());
    }

    /**
     * 策略匹配项，默认 {@code "*"}（匹配所有监听器），子类可覆写。
     */
    public String match() {
        return "*";
    }

    // ========================= 发布 =========================

    public void publish() {
        publish(getTopic(), getTag(), getTenantId());
    }

    public void publish(String topic) {
        publish(topic, getTag(), getTenantId());
    }

    public void publish(String topic, String tag) {
        publish(topic, tag, getTenantId());
    }

    /**
     * 发布 MQ 事件。
     *
     * <p>从 {@link BaseContext} 查找 {@link Consumer<MQEvent>}（由 {@link MQClient} 注册），
     * 找到后调用 {@code consumer.accept(this)} 把事件推送到 broker 生产者。
     */
    public void publish(String topic, String tag, String tenantId) {
        setTopic(topic);
        if (Objects.isNull(getTopic())) {
            MQProperties props = BaseContext
                    .<String, MQProperties>get(MQ_PROPERTIES);
            setTopic(Objects.nonNull(props) ? props.getDefaultTopic() : "DEFAULT");
        }
        setTag(tag);
        setTenantId(Objects.nonNull(tenantId) ? tenantId : ThreadContext.get(ContextConstants.TENANT_ID));
        setMsgId(Objects.isNull(getMsgId()) ? String.valueOf(System.currentTimeMillis()) : getMsgId());
        if (BaseContext.contains(MQ_EVENT_PUBLISHER)) {
            BaseContext.<String, Consumer<MQEvent>>get(MQ_EVENT_PUBLISHER).accept(this);
        } else {
            log.warn("MQEventPublisher (Consumer<MQEvent>) not registered in BaseContext, event {} not published",
                    getTopic());
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends MQEvent> T tenantId(String tenantId) {
        this.tenantId = tenantId;
        return (T) this;
    }
}
