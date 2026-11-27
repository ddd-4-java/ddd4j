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

    /** \u5f53\u524d\u6d88\u606f\u4ee3\u7406\u5ba2\u6237\u7aef\u4f7f\u7528\u7684\u8fde\u63a5\u548c\u8ba2\u9605\u914d\u7f6e\u3002 */
    private final DisruptorMQProperties properties;

    /**
     * 已注册监听器列表（{@link #initConsumer} 时累加，{@link #onEvent} 在 RingBuffer 回调里遍历）。
     */
    private final List<RegisteredListener> listeners = new CopyOnWriteArrayList<>();
    /** \u534f\u8c03\u6d88\u606f\u5ba2\u6237\u7aef\u542f\u52a8\u3001\u521d\u59cb\u5316\u548c\u5173\u95ed\u7684\u751f\u547d\u5468\u671f\u3002 */
    private final MQClientLifecycle lifecycle = new MQClientLifecycle();
    /** \u7ec4\u4ef6\u7684\u542f\u52a8\u72b6\u6001\u548c\u542f\u52a8\u5931\u8d25\u8bca\u65ad\u4fe1\u606f\u3002 */
    private final MQStartupStatus startupStatus = new MQStartupStatus("disruptor");
    /** \u6267\u884c\u4ee3\u7406\u5185\u90e8\u4e8b\u4ef6\u5206\u53d1\u7684 Disruptor\u3002 */
    private Disruptor<DisruptorEvent> disruptor;
    /** \u5411 Disruptor \u5de5\u4f5c\u7ebf\u7a0b\u8f6c\u4ea4\u6d88\u606f\u7684\u73af\u5f62\u7f13\u51b2\u533a\u3002 */
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
     * \u5411\u4e0a\u5bfb\u627e\u4e8c\u7684\u5e42\u6b21\u7f13\u51b2\u533a\u5927\u5c0f\uff0c\u9650\u5236\u5728\u5b9e\u73b0\u652f\u6301\u7684\u4e0a\u9650\u3002
     * @param requested \u914d\u7f6e\u4e2d\u8bf7\u6c42\u7684\u7f13\u51b2\u533a\u5927\u5c0f
     * @return \u7528\u4e8e\u521b\u5efa Disruptor \u7684\u7f13\u51b2\u533a\u5927\u5c0f
     */
    private static int normalizeBufferSize(int requested) {
        int size = 1;
        while (size < requested && size < (1 << 20)) {
            size <<= 1;
        }
        return size;
    }

    /**
     * \u505c\u6b62\u5f53\u524d\u6d88\u606f\u4f1a\u8bdd\u6216\u5206\u53d1\u5668\uff0c\u5e76\u6e05\u7406\u5176\u62e5\u6709\u7684\u53d1\u9001\u8d44\u6e90\u3002
     * @param disruptor \u5f85\u5173\u95ed\u7684 Disruptor \u5b9e\u4f8b
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
     * @param listener 监听器定义
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
     * RingBuffer \u4e8b\u4ef6\u56de\u8c03\u5165\u53e3\uff08{@link #start()} \u6ce8\u518c\u5230 Disruptor\uff09\u3002
     *
     *  <p>\u5339\u914d\u6d41\u7a0b\uff08\u7edf\u4e00\u9075\u5faa {@code namespace.topic[.tag]} \u8def\u7531\u6a21\u578b\uff09\uff1a
     *  <ol>
     *    <li>route key \u7cbe\u786e\u5339\u914d\uff1aevent \u7684 routeExpression vs \u6ce8\u518c\u65f6\u89e3\u6790\u7684 listener \u6709\u6548\u8def\u7531</li>
     *    <li>{@link TagMatcher} \u4e8c\u6b21\u8fc7\u6ee4\uff08\u5e94\u5bf9 listener.tags \u8868\u8fbe\u5f0f\u5982 {@code "paid || shipped"}\uff09</li>
     *    <li>\u53cd\u5e8f\u5217\u5316 + \u53cd\u5c04\u8c03\u7528\uff08{@link MQClient#consume} \u9ed8\u8ba4\u65b9\u6cd5\uff09</li>
     *  </ol>
     *  \u4e0d\u5339\u914d\u7684 listener \u8df3\u8fc7\uff0c\u907f\u514d\u8de8 namespace/topic/tag \u7684\u8bef\u6d3e\u53d1\u3002
     *
     * @param event \u73af\u5f62\u961f\u5217\u4e2d\u5206\u914d\u7ed9\u6d88\u8d39\u8005\u7684\u4e8b\u4ef6
     * @param sequence Disruptor \u73af\u5f62\u961f\u5217\u4e2d\u7684\u4e8b\u4ef6\u5e8f\u53f7
     * @param endOfBatch \u662f\u5426\u4e3a\u5f53\u524d\u6279\u6b21\u7684\u6700\u540e\u4e00\u4e2a\u4e8b\u4ef6
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

    /** 提供生命周期启动回调，当前实现的客户端在初始化阶段启动，此处无额外操作。 */
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
        /** \u5f53\u524d\u767b\u8bb0\u5e76\u63a5\u6536\u6d88\u606f\u7684\u4e1a\u52a1\u76d1\u542c\u5668\u3002 */
        private final MQListener listener;
        /** \u7528\u4e8e\u67e5\u627e\u5f53\u524d\u5206\u53d1\u76d1\u542c\u5668\u7684\u8def\u7531\u952e\u3002 */
        private final String routeKey;
    }
}
