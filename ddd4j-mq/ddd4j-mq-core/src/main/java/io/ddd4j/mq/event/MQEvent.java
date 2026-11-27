package io.ddd4j.mq.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * MQ 事件基类（纯 Java，零 Spring 依赖）。
 *
 * <p>业务事件继承本类后，通过 {@link #publish()} 发布到 MQ Broker。
 * 仅走 MQ 通道，进程内事件请使用 {@code io.ddd4j.core.ddd.event.DomainEvent}。
 *
 * <p>发布机制（多 broker 路由）：{@link #publish(String, String, String)} 从 {@link BaseContext}
 * 查找 key 为 {@link #MQ_EVENT_PUBLISHER} 的 {@code Map<String, Consumer<MQEvent>>}，
 * 按 {@link #broker} 字段（或全局 {@link MQProperties#getBroker()} 配置）匹配对应的 broker 生产者。
 * 每个 {@link MQClient} 在 {@code initProducer} 后以 {@link MQClient#impl()} 为 key 注册。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * public class OrderCreatedEvent extends MQEvent {
 *     private String orderId;
 *     private BigDecimal amount;
 * }
 *
 * // 1. 走全局默认 broker（配置 ddd4j.mq.broker=kafka）
 * new OrderCreatedEvent("OBS-001", BigDecimal.valueOf(99.9)).publish();
 *
 * // 2. 指定推送到 redisStream
 * new OrderCreatedEvent("OBS-001", BigDecimal.valueOf(99.9)).broker("redisStream").publish();
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
@Slf4j
@Data
@SuppressWarnings("unchecked")
public class MQEvent implements Serializable {

    /**
     * {@link BaseContext} key：MQ 事件发布者 Map（{@code Map<String, Consumer<MQEvent>>}）。
     * key = {@link MQClient#impl()} 返回值（如 {@code "kafka"} / {@code "rocket"} / {@code "redisStream"}），
     * value = {@link MQClient#initProducer(MQProperties)} 返回的发布函数。
     */
    public static final String MQ_EVENT_PUBLISHER = "MQEventPublisher";
    /**
     * {@link BaseContext} key：MQ 配置（{@link MQProperties}），
     * 用于获取 defaultTopic / namespace / broker 默认值。
     */
    public static final String MQ_PROPERTIES = "MQProperties";
    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * 消息 ID，默认当前时间戳
     */
    protected String msgId;
    /**
     * 主题，配置 {@code ddd4j.mq.default-topic} 后无须每次指定
     */
    protected String topic;
    /**
     * 标签，只支持单个标签，多标签需要分开发送
     */
    protected String tag;
    /**
     * namespace/topic/tag 拼接符，为空时由各 broker 实现决定默认值
     */
    protected String concat;
    /**
     * 租户 ID，默认从线程上下文获取（外部 JSON 常用 tenant_id）
     */
    @JsonAlias("tenant_id")
    protected String tenantId;
    /**
     * 目标 broker 标识（如 {@code "kafka"} / {@code "rocket"} / {@code "redisStream"}）。
     * <p>为空时走全局默认 broker（{@link MQProperties#getBroker()} 配置）。
     */
    protected String broker;
    /**
     * 命名空间，配置 {@code ddd4j.mq.namespace} 后无须每次指定
     */
    private String namespace;

    /**
     * \u7b56\u7565\u5339\u914d\uff1asupports \u53c2\u6570\u6765\u6e90\u4e8e {@code @MQEventListener.supports}\u3002
     *
     * @param supports \u662f\u5426\u652f\u6301\u7684\u5f00\u5173
     * @return \u6ee1\u8db3\u6761\u4ef6\u65f6\u8fd4\u56de true\uff0c\u5426\u5219\u8fd4\u56de false
     */
    public boolean supports(List<String> supports) {
        return supports.contains(match());
    }

    /**
     * \u7b56\u7565\u5339\u914d\u9879\uff0c\u9ed8\u8ba4 {@code "*"}\uff08\u5339\u914d\u6240\u6709\u76d1\u542c\u5668\uff09\uff0c\u5b50\u7c7b\u53ef\u8986\u5199\u3002
     *
     * @return \u5339\u914d\u7684\u5b57\u7b26\u4e32\u5185\u5bb9
     */
    public String match() {
        return "*";
    }

    // ========================= 发布 =========================

    /** \u6309\u4e8b\u4ef6\u81ea\u8eab\u7684\u4e3b\u9898\u3001\u6807\u7b7e\u548c\u79df\u6237\u53d1\u5e03\u3002 */
    public void publish() {
        publish(getTopic(), getTag(), getTenantId());
    }

    /**
     * \u53d1\u5e03\u5230\u6307\u5b9a\u4e3b\u9898\u3002
     * @param topic \u53d1\u5e03\u4e3b\u9898
     */
    public void publish(String topic) {
        publish(topic, getTag(), getTenantId());
    }

    /**
     * \u53d1\u5e03\u5230\u6307\u5b9a\u4e3b\u9898\u548c\u6807\u7b7e\u3002
     * @param topic \u53d1\u5e03\u4e3b\u9898
     * @param tag \u6d88\u606f\u6807\u7b7e
     */
    public void publish(String topic, String tag) {
        publish(topic, tag, getTenantId());
    }

    /**
     * \u53d1\u5e03 MQ \u4e8b\u4ef6\u3002
     *
     *  <p>\u4ece {@link BaseContext} \u67e5\u627e {@code Map<String, Consumer<MQEvent>>}\uff08\u7531\u5404 {@link MQClient} \u6ce8\u518c\uff09\uff0c
     *  \u6309\u4ee5\u4e0b\u4f18\u5148\u7ea7\u5339\u914d\u76ee\u6807 broker \u751f\u4ea7\u8005\uff1a
     *  <ol>
     *    <li>{@link #broker} \u5b57\u6bb5\u975e\u7a7a \u2192 \u7528\u6b64\u503c\u4f5c key \u67e5\u627e</li>
     *    <li>{@link MQProperties#getBroker()} \u5168\u5c40\u914d\u7f6e\u975e\u7a7a\u4e14\u975e {@code "none"} \u2192 \u7528\u6b64\u503c\u4f5c key \u67e5\u627e</li>
     *    <li>\u4ec5\u6ce8\u518c\u4e86\u4e00\u4e2a broker \u2192 \u76f4\u63a5\u7528\uff08\u4fbf\u6377\u573a\u666f\uff09</li>
     *    <li>\u90fd\u627e\u4e0d\u5230 \u2192 warn \u65e5\u5fd7\uff0c\u4e8b\u4ef6\u4e0d\u53d1\u5e03</li>
     *  </ol>
     *
     * @param topic \u6d88\u606f\u4e3b\u9898
     * @param tag \u6807\u7b7e
     * @param tenantId \u79df\u6237\u6807\u8bc6
     */
    public void publish(String topic, String tag, String tenantId) {
        setTopic(topic);
        if (Objects.isNull(getTopic())) {
            MQProperties props = BaseContext.get(MQ_PROPERTIES);
            setTopic(Objects.nonNull(props) ? props.getDefaultTopic() : "DEFAULT");
        }
        setTag(tag);
        setTenantId(Objects.nonNull(tenantId) ? tenantId : ThreadContext.get(ContextConstants.TENANT_ID));
        setMsgId(Objects.isNull(getMsgId()) ? UUID.randomUUID().toString() : getMsgId());

        Map<String, Consumer<MQEvent>> publishers = BaseContext.get(MQ_EVENT_PUBLISHER);
        if (Objects.isNull(publishers) || publishers.isEmpty()) {
            log.warn("No MQEventPublisher registered, event [{}] not published", getTopic());
            return;
        }

        // 按优先级查找目标 broker
        String targetBroker = getBroker();
        if (StrKit.isEmpty(targetBroker)) {
            MQProperties props = BaseContext.get(MQ_PROPERTIES);
            targetBroker = Objects.nonNull(props) ? props.getBroker() : null;
        }
        Consumer<MQEvent> publisher = null;
        if (StrKit.isNotEmpty(targetBroker) && !"none".equalsIgnoreCase(targetBroker)) {
            publisher = publishers.get(targetBroker);
        }
        if (Objects.isNull(publisher) && publishers.size() == 1) {
            publisher = publishers.values().iterator().next();
        }
        if (Objects.nonNull(publisher)) {
            publisher.accept(this);
        } else {
            log.warn("No MQEventPublisher found for broker=[{}], event [{}] not published. Registered: {}",
                    targetBroker, getTopic(), publishers.keySet());
        }
    }

    /**
     * \u94fe\u5f0f\u8bbe\u7f6e\u79df\u6237 ID\u3002
     *
     * @param <T> \u6570\u636e\u5143\u7d20\u7c7b\u578b
     * @param tenantId \u79df\u6237\u6807\u8bc6
     * @return \u8fd4\u56de\u7684 T \u7ed3\u679c
     */
    public <T extends MQEvent> T tenantId(String tenantId) {
        this.tenantId = tenantId;
        return (T) this;
    }

    /**
     * \u94fe\u5f0f\u8bbe\u7f6e\u76ee\u6807 broker\u3002
     * @param broker broker \u6807\u8bc6\uff08\u5982 {@code "kafka"} / {@code "rocket"} / {@code "redisStream"}\uff09
     * @return this
     *
     * @param <T> \u6570\u636e\u5143\u7d20\u7c7b\u578b
     */
    public <T extends MQEvent> T broker(String broker) {
        this.broker = broker;
        return (T) this;
    }
}
