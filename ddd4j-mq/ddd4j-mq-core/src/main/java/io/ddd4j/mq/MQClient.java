/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.ddd4j.mq;

import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.mq.annotation.MQEventListener;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.event.MQEventSerialization;
import io.ddd4j.mq.event.MQEventStorer;
import io.ddd4j.mq.lifecycle.MQClientLifecycle;
import io.ddd4j.mq.lifecycle.MQInitializationException;
import io.ddd4j.mq.lifecycle.MQListenerInitializationFailure;
import io.ddd4j.mq.lifecycle.MQStartupStatus;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.message.Acknowledgment;
import io.ddd4j.mq.util.TagMatcher;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * MQ 客户端契约（纯 Java，零 Spring 依赖）。100% 参考实现 base-mq {@code MQClient}。
 *
 * <p>接入新的 MQ 只需实现本接口，每个 broker 模块最终 3 个文件：
 * {@code XxxClient}（impl MQClient）+ {@code XxxAcknowledgment} + {@code XxxProperties}。
 *
 * <h3>核心流程</h3>
 * <ol>
 *   <li><b>发布</b>：{@link #initProducer} 返回 {@link Consumer}，
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
public interface MQClient extends AutoCloseable {

    /**
     * {@link BaseContext} key：MQ 序列化器
     */
    String MQ_SERIALIZATION = MQEvent.MQ_EVENT_PUBLISHER + ".serialization";
    /**
     * {@link BaseContext} key：MQ 事件持久化器
     */
    String MQ_STORER = MQEvent.MQ_EVENT_PUBLISHER + ".storer";

    /**
     * @return MQ 实现标识（如 {@code "kafka"} / {@code "rocket"} / {@code "rabbit"} / {@code "redis"} / {@code "redisStream"}）
     */
    String impl();

    /**
     * 整体初始化：注册依赖 → 初始化生产者 → 注册所有消费者。
     *
     * <p>由框架适配层（如 ddd4j-mq-spring）在应用就绪时调用。本方法会把 {@link #initProducer} 返回的
     * {@link Consumer} 注册到 {@link BaseContext}，{@link MQEvent#publish} 通过它推送消息。
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
        MQClientLifecycle lifecycle = lifecycle();
        MQStartupStatus startupStatus = startupStatus();
        int checkpoint = lifecycle.checkpoint();
        startupStatus.starting();
        MQProperties previousProperties = BaseContext.get(MQEvent.MQ_PROPERTIES);
        MQEventSerialization previousSerialization = BaseContext.get(MQ_SERIALIZATION);
        MQEventStorer previousStorer = BaseContext.get(MQ_STORER);
        // 注册配置与依赖到 BaseContext
        BaseContext.inject(MQEvent.MQ_PROPERTIES, properties);
        BaseContext.inject(MQ_SERIALIZATION, serialization);
        if (Objects.nonNull(storer)) {
            BaseContext.inject(MQ_STORER, storer);
        }
        // 初始化生产者，注册到 publishers Map（key=impl()，允许多 broker 共存）
        Consumer<MQEvent> producer;
        try {
            producer = initProducer(properties);
        } catch (RuntimeException exception) {
            MQListenerInitializationFailure failure = new MQListenerInitializationFailure(
                    impl(), "", "", "producer", true, exception.getClass().getSimpleName());
            startupStatus.failed(failure);
            MQInitializationException initializationException = new MQInitializationException(
                    failure.broker(), failure.topic(), failure.group(), failure.listenerMethod(), exception);
            try {
                lifecycle.rollback(checkpoint);
            } catch (RuntimeException rollbackFailure) {
                initializationException.addSuppressed(rollbackFailure);
            }
            Internals.restoreContext(previousProperties, previousSerialization, previousStorer);
            throw initializationException;
        }
        Map<String, Consumer<MQEvent>> publishers = null;
        Consumer<MQEvent> previousPublisher = null;
        Consumer<MQEvent> registeredProducer = null;
        if (Objects.nonNull(producer)) {
            log.info("Initializing MQEventPublisher for [{}]", impl());
            publishers = BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER);
            if (Objects.isNull(publishers)) {
                publishers = new ConcurrentHashMap<>();
                BaseContext.inject(MQEvent.MQ_EVENT_PUBLISHER, publishers);
            }
            MQPublisherRegistration activeProducer = new MQPublisherRegistration(producer);
            registeredProducer = activeProducer;
            previousPublisher = publishers.put(impl(), activeProducer);
            Map<String, Consumer<MQEvent>> registeredPublishers = publishers;
            lifecycle.register("publisher:" + impl(), () -> {
                activeProducer.deactivate();
                if (registeredPublishers.remove(impl(), activeProducer)
                        && registeredPublishers.isEmpty()
                        && BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER) == registeredPublishers) {
                    BaseContext.remove(MQEvent.MQ_EVENT_PUBLISHER);
                }
            });
        }
        // 初始化消费者
        log.info("Initializing MQEventListener for [{}]", impl());
        int success = 0;
        List<MQListener> safeListeners = Objects.isNull(listeners)
                ? Collections.emptyList() : listeners;
        for (MQListener listener : safeListeners) {
            int listenerCheckpoint = lifecycle.checkpoint();
            try {
                if (!initConsumer(listener, properties)) {
                    throw new IllegalStateException("MQ consumer initialization returned false");
                }
                success++;
            } catch (Exception e) {
                String topic = Objects.toString(listener.getTopic(), "");
                String group = Objects.toString(listener.getGroup(), "");
                String method = Objects.isNull(listener.getMethod()) ? "producer"
                        : listener.getMethod().getDeclaringClass().getName() + "#" + listener.getMethod().getName();
                MQListenerInitializationFailure failure = new MQListenerInitializationFailure(
                        impl(), topic, group, method, listener.isRequired(), e.getClass().getSimpleName());
                if (listener.isRequired()) {
                    startupStatus.failed(failure);
                    MQInitializationException initializationException = new MQInitializationException(
                            failure.broker(), failure.topic(), failure.group(), failure.listenerMethod(), e);
                    try {
                        lifecycle.rollback(checkpoint);
                    } catch (RuntimeException rollbackFailure) {
                        initializationException.addSuppressed(rollbackFailure);
                    }
                    if (Objects.nonNull(publishers)) {
                        if (publishers.remove(impl(), registeredProducer)
                                && publishers.isEmpty()
                                && BaseContext.get(MQEvent.MQ_EVENT_PUBLISHER) == publishers) {
                            BaseContext.remove(MQEvent.MQ_EVENT_PUBLISHER);
                        }
                        if (Objects.nonNull(previousPublisher)
                                && (!(previousPublisher instanceof MQPublisherRegistration)
                                || ((MQPublisherRegistration) previousPublisher).isActive())) {
                            publishers.putIfAbsent(impl(), previousPublisher);
                            BaseContext.inject(MQEvent.MQ_EVENT_PUBLISHER, publishers);
                        }
                    }
                    Internals.restoreContext(previousProperties, previousSerialization, previousStorer);
                    throw initializationException;
                }
                startupStatus.degraded(failure);
                try {
                    lifecycle.rollback(listenerCheckpoint);
                } catch (RuntimeException rollbackFailure) {
                    log.warn("Optional MQ listener [{}] rollback failed: {}",
                            listener.getRouteExpression(this.defaultConcat()),
                            rollbackFailure.getClass().getSimpleName());
                }
                log.warn("Optional MQ listener [{}] initialization failed: {}",
                        listener.getRouteExpression(this.defaultConcat()), failure.reason());
            }
        }
        startupStatus.ready();
        log.info("MQ [{}] listening {} listener(s)", impl(), success);

    }

    /**
     * 初始化生产者，返回 MQ 事件的发布函数。
     *
     * <p>返回的 {@link Consumer} 会被注册到 {@link BaseContext}，
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
     * 返回当前客户端拥有资源的生命周期。
     *
     * @return 生命周期；第三方实现默认不受托管
     */
    default MQClientLifecycle lifecycle() {
        return MQClientLifecycle.unmanaged();
    }

    /**
     * 返回当前客户端的结构化启动状态。
     *
     * @return 启动状态；第三方实现默认不受托管
     */
    default MQStartupStatus startupStatus() {
        return MQStartupStatus.unmanaged();
    }

    /**
     * 结束当前组件持有的资源。
     *
     * <p>此实现调用 <code>logger().info("Shutting down MQClient [{}]", impl())</code>。</p>
     */
    @Override
    default void close() {
        logger().info("Shutting down MQClient [{}]", impl());
        try {
            lifecycle().close();
        } finally {
            startupStatus().stopped();
        }
    }

    /**
     * 序列化器（从 {@link BaseContext} 查找，由 {@link #init} 注册）。
     * @return 返回的 MQEventSerialization 结果
     */
    default MQEventSerialization serialization() {
        return BaseContext.<String, MQEventSerialization>get(MQ_SERIALIZATION);
    }

    /**
     * MQ 配置（从 {@link BaseContext} 查找）。
     * @return 返回的 MQProperties 结果
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
        logger().info("Consume MQ [{}]: {}", listener.getRouteExpression(this.defaultConcat()), serialization().serialize(event));
        try {
            ThreadContext.set(ContextConstants.TENANT_ID, event.getTenantId());
            // MQ 事件持久化
            MQProperties properties = properties();
            if (Objects.nonNull(properties) && properties.isPersist()) {
                MQEventStorer storer = BaseContext.get(MQ_STORER);
                if (Objects.isNull(storer)) {
                    throw new IllegalStateException("persist=true requires an MQEventStorer");
                }
                storer.store(event);
            }
            // 反射调用 @MQEventListener 标注的监听方法
            listener.getMethod().invoke(listener.getBean(), event);
        } catch (Exception e) {
            throw Internals.unwrap(e);
        } finally {
            ThreadContext.clear();
        }
    }

    /**
     * \u4fbf\u6377\u91cd\u8f7d\uff1a\u65e0 Acknowledgment \u7684\u6d88\u8d39\uff08ack \u80fd\u529b\u7531 broker \u5185\u90e8\u5904\u7406\uff0c\u5982 RocketMQ \u8fd4\u56de\u503c\u8bed\u4e49\uff09\u3002
     * @param listener \u76d1\u542c\u5668
     * @param event \u4e8b\u4ef6
     * @throws java.lang.Throwable \u6267\u884c\u5bf9\u5e94\u64cd\u4f5c\u5931\u8d25\u65f6\u629b\u51fa
     *
     * @throws Throwable \u5df2\u89e3\u5305\u7684\u4e1a\u52a1\u5f02\u5e38
     */
    default void consume(MQListener listener, MQEvent event) throws Throwable {
        consume(listener, event, null);
    }

    // ========================= 物理地址拼接（生产者和消费者侧共享）=========================

    /**
     * 日志器（各实现可覆写自定义 topic）。
     * @return 返回的 Logger 结果
     */
    default Logger logger() {
        return LogHolder.logger();
    }

    /**
     * 解析最终的拼接符（concat）。
     *
     * <p>优先级：{@link MQEvent#concat} &gt; 当前 Client 默认值（{@link #defaultConcat()}）。
     * 注：不在 properties 层暴露 concat —— 避免全局配置覆盖 broker 惯例（Kafka 习惯 {@code "_"}、
     * Redis 习惯 {@code ":"}、MQTT 习惯 {@code "/"}）。如需差异化，由各 broker 自己的 Properties 类覆写。
     *
     * @param event MQ 事件（可为 null）
     * @return 三段式拼接符
     */
    default String concat(MQEvent event) {
        if (Objects.nonNull(event) && StrKit.isNotEmpty(event.getConcat())) {
            return event.getConcat();
        }
        return defaultConcat();
    }

    /**
     * Broker 默认拼接符（各 broker 决定，建议子类覆写）。
     *
     * <p>对齐 base-mq {@code MQListener.namespaceTopicTags()}：Rabbit/Rocket/Kafka 用 {@code "."}（Kafka 也常用 {@code "_"}），
     * Redis Stream 用 {@code ":"}，MQTT 用 {@code "/"}。
     *
     * @return broker 默认 concat
     */
    default String defaultConcat() {
        return ".";
    }

    /**
     * 解析命名空间：{@link MQEvent#namespace} 优先，回落到 {@link MQProperties#namespace}。
     *
     * @param event      MQ 事件（可为 null）
     * @param properties MQ 配置（不可为 null）
     * @return 命名空间，可为 null
     */
    default String namespace(MQEvent event, MQProperties properties) {
        if (Objects.nonNull(event) && StrKit.isNotEmpty(event.getNamespace())) {
            return event.getNamespace();
        }
        return namespace((String) null, properties);
    }

    /**
     * 字符串版命名空间解析（{@link MQListener#namespace} 直接传入）。
     *
     * @param namespace  显式命名空间（可为 null）
     * @param properties MQ 配置（可为 null）
     * @return 命名空间，可为 null
     */
    default String namespace(String namespace, MQProperties properties) {
        if (StrKit.isNotEmpty(namespace)) {
            return namespace;
        }
        if (Objects.nonNull(properties)) {
            return properties.getNamespace();
        }
        return null;
    }

    /**
     * 拼接物理地址：{@code namespace[concat]topic[concat]tag}（对齐 base-mq
     * {@code MQListener.namespaceTopicTags()}）。
     *
     * @param namespace 命名空间（可为 null）
     * @param topic     主题
     * @param tag       标签（可为 null）
     * @param concat    拼接符
     * @return 物理地址
     */
    default String resolveTopic(String namespace, String topic, String tag, String concat) {
        String sep = StrKit.isEmpty(concat) ? "." : concat;
        StringBuilder sb = new StringBuilder();
        if (StrKit.isNotEmpty(namespace)) {
            sb.append(namespace).append(sep);
        }
        sb.append(Objects.nonNull(topic) ? topic : "DEFAULT");
        if (StrKit.isNotEmpty(tag)) {
            sb.append(sep).append(tag);
        }
        return sb.toString();
    }

    /**
     * 便捷重载：从 {@link MQEvent} + {@link MQProperties} 解析物理地址（生产者侧）。
     * @param event 事件
     * @param properties 属性集合
     * @return 解析的字符串内容
     */
    default String resolveTopic(MQEvent event, MQProperties properties) {
        return resolveTopic(namespace(event, properties), event.getTopic(), event.getTag(), concat(event));
    }

    /**
     * 便捷重载：从 {@link MQListener} + {@link MQProperties} 解析物理目的地（消费者侧，
     * tag 取监听器声明的 tags 首个正向 tag，保持订阅定位一致）。
     * @param listener 监听器
     * @param properties 属性集合
     * @return 解析的字符串内容
     */
    default String resolveTopic(MQListener listener, MQProperties properties) {
        return resolveTopic(namespace(listener.getNamespace(), properties),
                listener.getTopic(),
                TagMatcher.findIncludes(listener.getTags()).stream().findFirst().orElse(null),
                concat(null));
    }

    /**
     * Listener 端的物理路由键（{@code namespace.topic[.tag]}），
     * 用 {@link #defaultConcat()} 拼接，确保与 producer 端 {@link #resolveTopic(MQEvent, MQProperties)} 同规则。
     *
     * <p>对齐 ddd4j Disruptor 事件的 {@code DisruptorEvent.getRouteExpression()}。
     *
     * @param listener 监听器定义
     * @return 物理路由键
     */
    default String resolveRouteKey(MQListener listener) {
        return listener.getRouteExpression(defaultConcat());
    }

    /**
     * 业务路由 key（partition/sharding/ordered-delivery key）。
     *
     * <p>本质是「业务维度的顺序保证键」——Kafka 用它做 partition 路由（同 key 进同 partition），
     * RabbitMQ 用 JMSXGroupId、Pulsar 用 partition key、RocketMQ 用 messageQueueSelector。
     * 默认实现：{@code tag+"\|"+tenant}（同 tag 同租户顺序保留）。broker 可覆写。
     *
     * <p>子类可通过 {@link PartitionKeyStrategy} 选择内置策略（CUSTOM 时应重写本方法）。
     *
     * @param event MQ 事件（可为 null，null 时返回 null）
     * @return 路由 key，{@code null} 表示不设 key（broker 自由路由）
     */
    default String partitionKey(MQEvent event) {
        if (Objects.isNull(event)) {
            return null;
        }
        String tag = event.getTag();
        String tenant = event.getTenantId();
        if (Objects.nonNull(tag) && Objects.nonNull(tenant)) {
            return tag + "|" + tenant;
        }
        return Objects.nonNull(tag) ? tag : tenant;
    }

    /**
     * tag 消息头的 key（producer 写入 + consumer 读取 + selector 引用）统一用同一个常量。
     *
     * <p>默认 {@code "ddd4jTag"}（无 {@code .}，保证是合法 SQL-92 identifier，
     * JMS Message Selector 可直接当 property 名用）。broker 可覆写。
     *
     * <p>应用层读 header 仍可读 {@link io.ddd4j.mq.message.MessageHeaders#HEADER_DESTINATION_TAG}
     * 作为兼容，但 selector 必须用此 key。
     * @return 返回的字符串内容
     */
    default String tagHeaderKey() {
        return "ddd4jTag";
    }

    /**
     * 将 {@link MQEventListener#tags()} 表达式翻译为 broker 端 selector（SQL-92 子集），
     * 让订阅时直接传给 broker 做 broker 端过滤（不投递到 listener）。适用于
     * ActiveMQ Artemis、RocketMQ、Kafka 等支持 JMS Message Selector / 订阅 selector 的 broker。
     *
     * <p>对应规则：
     * <ul>
     *   <li>{@code "*"} / {@code null} / 空 → {@code null}（不过滤，所有都投递给 listener）</li>
     *   <li>{@code "paid"} → {@code "tag = 'paid'"}（精确匹配）</li>
     *   <li>{@code "paid || shipped"} → {@code "(tag = 'paid' OR tag = 'shipped')"}</li>
     *   <li>{@code "* -cancelled"} → {@code "(tag <> 'cancelled' OR tag IS NULL)"}
     *       （= 全部但排除 cancelled，无 tag 也允许）</li>
     *   <li>{@code "paid -cancelled"} → {@code "(tag = 'paid' AND (tag <> 'cancelled' OR tag IS NULL))"}
     *       （= paid 或无 tag，但不包含 cancelled）</li>
     * </ul>
     *
     * <p>property name 通过 {@link #tagHeaderKey()} 获取，SQL 字符串字面量用 {@code '} 包。
     *
     * @param tags 监听器声明的 tag 表达式（{@code null} / 空 / {@code "*"} → 不过滤）
     * @return JMS selector 字符串，{@code null} 表示不过滤（由调用方决定是用 broker 还是 fallback 应用层）
     */
    default String tagsToSelector(String tags) {
        if (StrKit.isBlank(tags)) {
            return null;
        }
        // 解析 includes/excludes（与 TagMatcher 语义对齐：* 是通配符，不是字面量）
        Set<String> includes = new LinkedHashSet<>();
        boolean wildcard = false;
        Set<String> excludes = new LinkedHashSet<>();
        for (String token : StrKit.isBlank(tags) ? new String[0]
                : tags.replace("||", " ").trim().split("\\s+")) {
            String t = token.trim();
            if (StrKit.isEmpty(t) || "||".equals(t)) {
                continue;
            }
            if ("*".equals(t)) {
                wildcard = true;
            } else if (t.startsWith("-") && t.length() > 1) {
                excludes.add(t.substring(1));
            } else {
                includes.add(t);
            }
        }
        if (includes.isEmpty() && excludes.isEmpty()) {
            return null;
        }
        // "*" + 仅 excludes：includes 视为通配（1=1）
        if (wildcard) {
            includes = new LinkedHashSet<>();
        }
        if (includes.isEmpty() && excludes.isEmpty()) {
            return null;
        }
        String prop = tagHeaderKey();
        StringBuilder sb = new StringBuilder();
        if (!includes.isEmpty()) {
            // includes: tag IN (...) OR tag IS NULL（与 TagMatcher 一致：tag 为空时也算匹配）
            sb.append("(");
            boolean first = true;
            for (String i : includes) {
                if (!first) {
                    sb.append(" OR ");
                }
                sb.append(prop).append(" = '").append(Internals.escape(i)).append("'");
                first = false;
            }
            sb.append(" OR ").append(prop).append(" IS NULL)");
        }
        if (!excludes.isEmpty()) {
            StringBuilder exclude = new StringBuilder();
            boolean first = true;
            for (String e : excludes) {
                if (!first) {
                    exclude.append(" AND ");
                }
                exclude.append("(").append(prop).append(" <> '").append(Internals.escape(e)).append("' OR ").append(prop).append(" IS NULL)");
                first = false;
            }
            String left = sb.length() == 0 ? "1=1" : sb.toString();
            sb = new StringBuilder();
            sb.append("(").append(left).append(" AND ").append(exclude).append(")");
        }
        return sb.toString();
    }

    /**
     * 是否在 broker 端做 tag 过滤（不是所有 broker 都支持，返回 false 时调用方应 fallback 应用层）。
     *
     * <p>默认 true 表示「我会用 tagsToSelector 传给 broker」。子 broker 可覆写返回 false 强制应用层过滤
     * （如 Redis Stream / MQTT 等无 selector 机制的 broker）。
     * @return 满足条件时返回 true，否则返回 false
     */
    default boolean supportsBrokerTagFilter() {
        return true;
    }

    /**
     * Partition/路由策略枚举（broker 可读取此枚举决定 partitionKey 取值）。
     *
     * <p>NONE：不设 key（轮询路由，性能最佳但无顺序保证）。
     * <p>TAG：按 event.tag（同 tag 顺序）。
     * <p>TENANT：按 event.tenantId（同租户顺序）。
     * <p>TAG_TENANT：按 tag+tenant 复合 key（推荐，最常用）。
     * <p>CUSTOM：业务子类覆写 {@link #partitionKey(MQEvent)}，本枚举不适用。
     */
    enum PartitionKeyStrategy {
        /** \u4e0d\u8bbe\u7f6e\u5206\u533a\u952e\u3002 */
        NONE,
            /** \u6309\u6d88\u606f\u6807\u7b7e\u5206\u533a\u3002 */
            TAG,
            /** \u6309\u79df\u6237\u6807\u8bc6\u5206\u533a\u3002 */
            TENANT,
            /** \u6309\u6807\u7b7e\u548c\u79df\u6237\u590d\u5408\u5206\u533a\u3002 */
            TAG_TENANT,
            /** \u7531\u4e1a\u52a1\u5b9e\u73b0\u81ea\u5b9a\u4e49\u5206\u533a\u952e\u3002 */
            CUSTOM
    }

    /**
     * JDK8 兼容工具（接口私有静态方法为 JDK9+ 特性，收敛到嵌套类）。
     */
    final class Internals {

        /** 初始化 <code>Internals</code> 并委托其默认构造路径。 */
        private Internals() {
        }

        /**
         * 执行 <code>restore Context</code>，调用 <code>BaseContext.remove</code>、<code>BaseContext.inject</code>。
         *
         * @param properties 传入此操作的 <code>MQProperties</code> 类型 <code>properties</code>
         * @param serialization 传入此操作的 <code>MQEventSerialization</code> 类型 <code>serialization</code>
         * @param storer 传入此操作的 <code>MQEventStorer</code> 类型 <code>storer</code>
         */
        static void restoreContext(MQProperties properties, MQEventSerialization serialization,
                                   MQEventStorer storer) {
            if (Objects.isNull(properties)) {
                BaseContext.remove(MQEvent.MQ_PROPERTIES);
            } else {
                BaseContext.inject(MQEvent.MQ_PROPERTIES, properties);
            }
            if (Objects.isNull(serialization)) {
                BaseContext.remove(MQ_SERIALIZATION);
            } else {
                BaseContext.inject(MQ_SERIALIZATION, serialization);
            }
            if (Objects.isNull(storer)) {
                BaseContext.remove(MQ_STORER);
            } else {
                BaseContext.inject(MQ_STORER, storer);
            }
        }

        /**
         * \u7b80\u5355\u8f6c\u4e49\u5355\u5f15\u53f7\uff08\u9632\u6b62 tags \u5185\u542b\u5355\u5f15\u53f7\u7834\u574f selector \u89e3\u6790\uff09\u3002
         *
         * @param s 传入此操作的 <code>String</code> 类型 <code>s</code>
         * @return 返回表达式 <code>s.replace("'", "''")</code>
         */
        static String escape(String s) {
            return s.replace("'", "''");
        }

        /**
         * \u53cd\u5c04\u8c03\u7528\u5f02\u5e38\u89e3\u5305\uff08\u5bf9\u9f50 base-mq\uff09\u3002
         *
         * @param ex 传入此操作的 <code>Exception</code> 类型 <code>ex</code>
         * @return 按条件返回 <code>cause.getCause()</code>、<code>Objects.nonNull(cause) ? cause : ex</code>
         */
        static Throwable unwrap(Exception ex) {
            Throwable cause = ex.getCause();
            if (Objects.nonNull(cause) && Objects.nonNull(cause.getCause())) {
                return cause.getCause();
            }
            return Objects.nonNull(cause) ? cause : ex;
        }
    }

    /**
     * 兼容 {@link #logger()} 的共享 SLF4J 日志持有器。
     */
    @Slf4j(topic = "### DDD4J-MQ ###")
    final class LogHolder {

        /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>LogHolder</code> \u5b9e\u4f8b\u3002 */
        private LogHolder() {
        }

        /**
         * \u517c\u5bb9 {@link #logger()} \u7684\u5171\u4eab SLF4J \u65e5\u5fd7\u6301\u6709\u5668\u3002
         *
         * @return \u6d88\u606f\u5ba2\u6237\u7aef\u65e5\u5fd7\u8bb0\u5f55\u5668
         */

        private static Logger logger() {
            return log;
        }
    }
}
