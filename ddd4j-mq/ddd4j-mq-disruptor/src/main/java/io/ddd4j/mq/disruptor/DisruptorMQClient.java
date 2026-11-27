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
package io.ddd4j.mq.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import io.ddd4j.mq.MQClient;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.event.MQEvent;
import io.ddd4j.mq.lifecycle.MQClientLifecycle;
import io.ddd4j.mq.lifecycle.MQStartupStatus;
import io.ddd4j.mq.listener.MQListener;
import io.ddd4j.mq.util.TagMatcher;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * LMAX Disruptor（进程内 MQ）客户端实现（纯 Java，零 Spring 依赖）。
 *
 * <p>使用 ddd4j 自己的 {@link DisruptorEvent} 作为 RingBuffer 事件类型，
 * 以确保本地消息能力可由公开仓库独立构建。
 *
 * <h2>路由模型</h2>
 * <pre>
 *   namespace.topic.tag
 *   └───┬───┘ └─┬─┘ └┬┘
 *    环境隔离  业务分类  细分标签
 * </pre>
 *
 * <p>Disruptor 是进程内 RingBuffer，不存在可跨进程传输的 protocol header；稳定消息 ID 由
 * {@link MQEvent#getMsgId()} 同时保留在 payload 与 {@link DisruptorEvent} 字段中，不伪造
 * {@code ddd4j-message-id} metadata。
 *
 * <p>主线只有 {@link #initProducer(MQProperties)} 与 {@link #initConsumer(MQListener, MQProperties)}，
 * 核心业务逻辑全部内联。消费者模型：单 {@link com.lmax.disruptor.EventHandler} 内部遍历所有已注册 listener
 * （按 tag 过滤分发），对齐 base-mq RocketClient 的「集中消费者 + 应用层 tag 过滤」风格。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-MQ : DisruptorMQClient ###")
public class DisruptorMQClient implements MQClient {

    /**
     * 当前消息代理客户端使用的连接和订阅配置。
     */
    private final DisruptorMQProperties properties;

    /**
     * 已注册监听器列表（{@link #initConsumer} 时累加，{@link #onEvent} 在 RingBuffer 回调里遍历）。
     */
    private final List<RegisteredListener> listeners = new CopyOnWriteArrayList<>();
    /**
     * 协调消息客户端启动、初始化和关闭的生命周期。
     */
    private final MQClientLifecycle lifecycle = new MQClientLifecycle();
    /**
     * 组件的启动状态和启动失败诊断信息。
     */
    private final MQStartupStatus startupStatus = new MQStartupStatus("disruptor");
    /**
     * 执行代理内部事件分发的 Disruptor。
     */
    private Disruptor<DisruptorEvent> disruptor;
    /**
     * 向 Disruptor 工作线程转交消息的环形缓冲区。
     */
    @Getter
    private RingBuffer<DisruptorEvent> ringBuffer;

    /**
     * 双构造 1：传入配置，自建 RingBuffer 与 Disruptor（构造即启动，consumer 立即可用）。
     *
     * @param properties Disruptor 消息配置
     */
    public DisruptorMQClient(DisruptorMQProperties properties) {
        this.properties = Objects.requireNonNull(properties, "DisruptorMQ properties is required");
        this.startRingBuffer();
    }

    /**
     * 双构造 2：传入已初始化的 RingBuffer（runtime 集成复用同一 RingBuffer，事件类型必须为 DisruptorEvent）。
     * 不启动 Disruptor —— RingBuffer 已就绪，调用方负责管理其生命周期。
     *
     * @param ringBuffer 已初始化的事件缓冲区
     */
    public DisruptorMQClient(RingBuffer<DisruptorEvent> ringBuffer) {
        this.properties = new DisruptorMQProperties();
        this.ringBuffer = Objects.requireNonNull(ringBuffer, "DisruptorMQ ringBuffer is required");
    }

    /**
     * 向上寻找二的幂次缓冲区大小，限制在实现支持的上限。
     *
     * @param requested 配置中请求的缓冲区大小
     * @return 用于创建 Disruptor 的缓冲区大小
     */
    private static int normalizeBufferSize(int requested) {
        int size = 1;
        while (size < requested && size < (1 << 20)) {
            size <<= 1;
        }
        return size;
    }

    /**
     * 停止当前消息会话或分发器，并清理其拥有的发送资源。
     *
     * @param disruptor 待关闭的 Disruptor 实例
     */
    private static void shutdown(Disruptor<DisruptorEvent> disruptor) {
        try {
            disruptor.shutdown();
        } catch (RuntimeException exception) {
            disruptor.halt();
            throw exception;
        }
    }

    // ========================= 生产者 =========================

    /**
     * 返回此消息适配器在 DDD4J 中登记的实现名。
     *
     * @return 实际返回<code>"disruptor"</code>，供调用方取得当前值
     */
    @Override
    public String impl() {
        return "disruptor";
    }

    /**
     * 返回管理连接、生产者和消费者资源的生命周期对象。
     *
     * @return 当前消息适配器用于登记和关闭资源的生命周期对象
     */
    @Override
    public MQClientLifecycle lifecycle() {
        return lifecycle;
    }

    /**
     * 返回消息客户端当前记录的启动状态。
     *
     * @return 当前消息适配器记录的启动状态
     */
    @Override
    public MQStartupStatus startupStatus() {
        return startupStatus;
    }

    // ========================= 消费者 =========================

    /**
     * 准备消息生产者并返回可发布事件的入口。
     *
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 接收待发布事件的函数
     */
    @Override
    public Consumer<MQEvent> initProducer(MQProperties mqProperties) {
        return event -> {
            String topic = resolveTopic(event, mqProperties);
            String payload = serialization().serialize(event);
            long sequence = ringBuffer.next();
            try {
                DisruptorEvent e = ringBuffer.get(sequence);
                // 保留原始路由分量，由 DisruptorEvent 统一拼接一次。
                e.setTopic(event.getTopic());
                e.setTag(event.getTag());             // private 字段走 setter
                e.setNamespace(namespace(event, mqProperties));
                e.setMessageId(event.getMsgId());
                e.setPayload(payload);                // private 字段走 setter
                e.setSequence(sequence);
            } finally {
                ringBuffer.publish(sequence);
            }
            log.info("Publish MQ [{}]: {}", topic, payload);
        };
    }

    /**
     * 为监听器建立消息消费所需的订阅和客户端资源。
     *
     * <p>此实现调用 <code>listeners.add(new RegisteredListener(listener, resolveTopic(listener, mqProperties)))</code>。</p>
     *
     * @param listener     监听器定义
     * @param mqProperties 本次生产或订阅使用的消息客户端公共配置
     * @return 此实现返回<code>true</code>
     */
    @Override
    public boolean initConsumer(MQListener listener, MQProperties mqProperties) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(new RegisteredListener(listener, resolveTopic(listener, mqProperties)));
        log.info("Registered Disruptor consumer: bean={}, method={}",
                listener.getMethod().getDeclaringClass().getSimpleName(),
                listener.getMethod().getName());
        return true;
    }

    /**
     * RingBuffer 事件回调入口（{@link #start()} 注册到 Disruptor）。
     *
     * <p>匹配流程（统一遵循 {@code namespace.topic[.tag]} 路由模型）：
     * <ol>
     *   <li>route key 精确匹配：event 的 routeExpression vs 注册时解析的 listener 有效路由</li>
     *   <li>{@link TagMatcher} 二次过滤（应对 listener.tags 表达式如 {@code "paid || shipped"}）</li>
     *   <li>反序列化 + 反射调用（{@link MQClient#consume} 默认方法）</li>
     * </ol>
     * 不匹配的 listener 跳过，避免跨 namespace/topic/tag 的误派发。
     *
     * @param event      环形队列中分配给消费者的事件
     * @param sequence   Disruptor 环形队列中的事件序号
     * @param endOfBatch 是否为当前批次的最后一个事件
     */
    private void onEvent(DisruptorEvent event, long sequence, boolean endOfBatch) {
        String eventRouteKey = event.getRouteExpression();   // namespace.topic[.tag]
        String eventTag = event.getTag();
        for (RegisteredListener registered : listeners) {
            MQListener listener = registered.listener;
            try {
                // 1. 按注册时解析的有效路由匹配，包含全局 namespace 回退。
                if (!eventRouteKey.equals(registered.routeKey)) {
                    continue;
                }
                // 2. TagMatcher 二次过滤（应对 listener.tags 表达式）
                if (!TagMatcher.match(eventTag, listener.getTags())) {
                    continue;
                }
                // 3. 反序列化 + 反射调用
                Object payload = event.getPayload();
                MQEvent mqEvent = serialization().deserialize(payload, listener.payloadType());
                if (Objects.isNull(mqEvent)) {
                    log.warn("Disruptor consume [{}] failed: mqEvent is null", eventRouteKey);
                    continue;
                }
                DisruptorAcknowledgment ack = new DisruptorAcknowledgment(event, ringBuffer, sequence);
                consume(listener, mqEvent, ack);
                if (!ack.isAcknowledged()) {
                    ack.ackSingle();
                }
            } catch (Throwable ex) {
                log.error("Consume MQ [{}] failed", eventRouteKey, ex);
            }
        }
    }

    /**
     * 提供生命周期启动回调，当前实现的客户端在初始化阶段启动，此处无额外操作。
     */
    @Override
    public void start() {
        // Disruptor 在双构造 1 立即启动（双构造 2 RingBuffer 已就绪），
        // 此处留空对齐 MQClient.start() 契约。
    }

    /**
     * 启动 RingBuffer 与 Disruptor（双构造 1 自动调用，双构造 2 不调用）。
     */
    private void startRingBuffer() {
        int bufferSize = normalizeBufferSize(properties.getBufferSize());
        WaitStrategy waitStrategy = properties.getWaitStrategy().instance();
        Disruptor<DisruptorEvent> d = new Disruptor<>(DisruptorEvent::new, bufferSize,
                DaemonThreadFactory.INSTANCE, ProducerType.MULTI, waitStrategy);
        d.handleEventsWith(this::onEvent);
        ringBuffer = d.start();
        this.disruptor = d;
        lifecycle.register("disruptor", () -> shutdown(d));
        log.info("Disruptor RingBuffer started: bufferSize={}, waitStrategy={}",
                bufferSize, waitStrategy.getClass().getSimpleName());
    }

    /**
     * 关闭底层 Disruptor（应用关闭或测试清理时调用）。
     */
    @Override
    public void close() {
        try {
            lifecycle.close();
        } finally {
            startupStatus.stopped();
        }
    }

    /**
     * 保留原监听器和注册时按配置解析的有效路由，避免修改共享监听器对象。
     */
    @RequiredArgsConstructor
    private static final class RegisteredListener {
        /**
         * 当前登记并接收消息的业务监听器。
         */
        private final MQListener listener;
        /**
         * 用于查找当前分发监听器的路由键。
         */
        private final String routeKey;
    }
}
