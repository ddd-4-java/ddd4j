package io.ddd4j.mq;

import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.event.MQEventStorer;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.util.TagMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * MQ 客户端契约（纯 Java，零 Spring 依赖）。100% 参考实现 base-mq {@code MQClient}。
 *
 * <p>接入新的 MQ 只需实现本接口，每个 broker 模块最终 3 个文件：
 * {@code XxxClient}（impl MQClient）+ {@code XxxAcknowledgment} + {@code XxxProperties}。
 *
 * <h3>核心流程</h3>
 * <ol>
 *   <li><b>发布</b>：{@link #initProducer(MQProperties)} 返回 {@link Consumer<MQEvent>}，
 *       由 {@link #init(List, MQProperties, MQEventSerialization, MQEventStorer)} 注册到 {@link BaseContext}
 *       key 为 {@link MQEvent#MQ_EVENT_PUBLISHER}。{@link MQEvent#publish()} 通过该 Consumer 推送到底层生产者</li>
 *   <li><b>消费</b>：{@link #initConsumer(MQListener, MQProperties)} 建立原生消费者，
 *       收到消息后反序列化为 {@link MQEvent}，调 {@link #consume(MQListener, MQEvent, Acknowledgment)} 统一处理，
 *       最终反射调用 {@code @MQEventListener} 标注的监听方法</li>
 *   <li><b>确认</b>：各实现构建 broker 专属 {@link Acknowledgment} 传入 consume，
 *       实现不同级别的 ack（单条/批量/requeue/DLQ），解决 base-mq ack 能力弱的问题</li>
 * </ol>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public interface MQClient {

    /** {@link BaseContext} key：MQ 序列化器 */
    String MQ_SERIALIZATION = MQEvent.MQ_EVENT_PUBLISHER + ".serialization";
    /** {@link BaseContext} key：MQ 事件持久化器 */
    String MQ_STORER = MQEvent.MQ_EVENT_PUBLISHER + ".storer";

    /**
     * @return MQ 实现标识（如 {@code "kafka"} / {@code "rocket"} / {@code "rabbit"} / {@code "redis"} / {@code "redisStream"}）
     */
    String impl();

    /**
     * 整体初始化：注册依赖 → 初始化生产者 → 注册所有消费者。
     *
     * <p>由框架适配层（如 ddd4j-mq-spring）在应用就绪时调用。本方法会把 {@link #initProducer} 返回的
     * {@link Consumer<MQEvent>} 注册到 {@link BaseContext}，{@link MQEvent#publish()} 通过它推送消息。
     *
     * @param listeners     已扫描好的监听器列表
     * @param properties    MQ 配置（同时注册到 {@link BaseContext}，供 MQEvent 读 defaultTopic）
     * @param serialization 序列化器
     * @param storer        事件持久化器（可为 null）
     */
    default void init(List<MQListener> listeners, MQProperties properties,
                      MQEventSerialization serialization, MQEventStorer storer) {
        if (!properties.isEnabled() || !Objects.equals(properties.getBroker(), impl())) {
            return;
        }
        Logger log = logger();
        // 注册配置与依赖到 BaseContext
        BaseContext.inject(MQEvent.MQ_PROPERTIES, properties);
        BaseContext.inject(MQ_SERIALIZATION, serialization);
        if (Objects.nonNull(storer)) {
            BaseContext.inject(MQ_STORER, storer);
        }
        // 初始化生产者，注册为 MQEventPublisher（Consumer<MQEvent>）
        Consumer<MQEvent> producer = initProducer(properties);
        if (Objects.nonNull(producer) && !BaseContext.contains(MQEvent.MQ_EVENT_PUBLISHER)) {
            log.info("Initializing MQEventPublisher for [{}]", impl());
            BaseContext.inject(MQEvent.MQ_EVENT_PUBLISHER, producer);
        }
        // 初始化消费者
        log.info("Initializing MQEventListener for [{}]", impl());
        int success = 0;
        for (MQListener listener : listeners) {
            try {
                if (initConsumer(listener, properties)) {
                    success++;
                }
            } catch (Exception e) {
                log.error("Listen MQ [{}] failed!", listener.namespaceTopicTags(), e);
            }
        }
        log.info("MQ [{}] listening {} listener(s)", impl(), success);
    }

    /**
     * 初始化生产者，返回 MQ 事件的发布函数。
     *
     * <p>返回的 {@link Consumer<MQEvent>} 会被注册到 {@link BaseContext}，
     * {@link MQEvent#publish()} 调用 {@code consumer.accept(event)} 把消息推送到 broker 生产者。
     *
     * @param properties MQ 配置
     * @return 发布函数，null 表示不提供发布能力
     */
    Consumer<MQEvent> initProducer(MQProperties properties);

    /**
     * 初始化单个消费者。
     *
     * <p>实现方在此建立 broker 原生消费者，收到消息后：
     * <ol>
     *   <li>用 {@link TagMatcher} 做 tag 过滤</li>
     *   <li>反序列化为 {@link MQEvent}（用 {@link #serialization()}）</li>
     *   <li>构建 broker 专属 {@link Acknowledgment}</li>
     *   <li>调用 {@link #consume(MQListener, MQEvent, Acknowledgment)} 完成统一消费</li>
     * </ol>
     *
     * @param listener   监听器定义
     * @param properties MQ 配置
     * @return 是否初始化成功
     * @throws Exception 初始化异常
     */
    boolean initConsumer(MQListener listener, MQProperties properties) throws Exception;

    /**
     * 启动（如统一启动所有消费者）。
     */
    default void start() {
    }

    /**
     * 序列化器（从 {@link BaseContext} 查找，由 {@link #init} 注册）。
     */
    default MQEventSerialization serialization() {
        return BaseContext.<String, MQEventSerialization>get(MQ_SERIALIZATION);
    }

    /**
     * MQ 配置（从 {@link BaseContext} 查找）。
     */
    default MQProperties properties() {
        return BaseContext.<String, MQProperties>get(MQEvent.MQ_PROPERTIES);
    }

    /**
     * 消费单个 MQ 事件（核心消费逻辑，由各 {@code initConsumer} 实现内调用）。
     *
     * <p>统一完成：策略匹配 → 租户注入 → 持久化 → 反射调用 {@code @MQEventListener} 方法 → 异常解包。
     * 调用方在 consume 返回或抛异常后，根据 {@link Acknowledgment} 决定 ack/nack/requeue/DLQ。
     *
     * @param listener 监听器定义
     * @param event    已反序列化的 MQ 事件
     * @param ack      确认端口（可为 null，表示该 broker 无 ack 能力）
     * @throws Throwable 业务异常（已解包，调用方据此决定 nack/requeue/DLQ）
     */
    default void consume(MQListener listener, MQEvent event, Acknowledgment ack) throws Throwable {
        if (Objects.isNull(event)) {
            return;
        }
        if (!event.supports(listener.supports())) {
            return;
        }
        logger().info("Consume MQ [{}]: {}", listener.namespaceTopicTags(), serialization().serialize(event));
        try {
            ThreadContext.set(ContextConstants.TENANT_ID, event.getTenantId());
            // MQ 事件持久化
            MQProperties properties = properties();
            if (Objects.nonNull(properties) && properties.isPersist()) {
                MQEventStorer storer = BaseContext.get(MQ_STORER);
                if (Objects.nonNull(storer)) {
                    try {
                        storer.store(event);
                    } catch (Exception e) {
                        logger().error("Persist MQ failed [{}]: {}", listener.namespaceTopicTags(),
                                serialization().serialize(event), e);
                    }
                }
            }
            // 反射调用 @MQEventListener 标注的监听方法
            listener.getMethod().invoke(listener.getBean(), event);
        } catch (Exception e) {
            throw unwrap(e);
        } finally {
            ThreadContext.clear();
        }
    }

    /**
     * 便捷重载：无 Acknowledgment 的消费（ack 能力由 broker 内部处理，如 RocketMQ 返回值语义）。
     */
    default void consume(MQListener listener, MQEvent event) throws Throwable {
        consume(listener, event, null);
    }

    /**
     * 日志器（各实现可覆写自定义 topic）。
     */
    default Logger logger() {
        return LoggerFactory.getLogger("### DDD4J-MQ : " + impl() + "Client ###");
    }

    /**
     * 反射调用异常解包（对齐 base-mq）。
     */
    private static Throwable unwrap(Exception ex) {
        Throwable cause = ex.getCause();
        if (Objects.nonNull(cause) && Objects.nonNull(cause.getCause())) {
            return cause.getCause();
        }
        return Objects.nonNull(cause) ? cause : ex;
    }
}
