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
package io.ddd4j.mq.event;

import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityId;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.mq.serialization.JsonMQEventSerialization;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/**
 * 领域事件到 MQ 的跨进程转发器。
 *
 * <p>实现 {@link DomainEventPublisher}，将 core 领域事件转换为 {@link DomainEventCarrier}
 * 并通过已注册的 MQ 生产者发送到消息队列，实现跨进程事件传播。
 *
 * <h3>接入点选择</h3>
 * <p>本类委托 {@link MQEvent#publish()} 完成实际发送。该机制从 {@link io.ddd4j.core.context.BaseContext}
 * 查找已注册的 MQ 生产者 Map（由各 {@link io.ddd4j.mq.MQClient} 在初始化时注册），
 * 按 broker 配置自动路由到目标 MQ 实现（Kafka / Pulsar / NATS 等）。
 *
 * <h3>Topic / Tag 设计</h3>
 * <ul>
 *   <li><b>topic</b>：取 {@link DomainEvent#getEventType()} 的值（即事件类简单名称，如 {@code "OrderCreatedEvent"}）</li>
 *   <li><b>tag</b>：固定 {@code "domain-event"}，便于消费者统一批量订阅所有领域事件</li>
 *   <li><b>payload</b>：领域事件 JSON 序列化结果，通过 {@link DomainEventCarrier} 携带</li>
 * </ul>
 *
 * <h3>失败语义</h3>
 * <p>与 {@link MQEvent#publish()} 保持一致——不抛异常，而是 warn 日志。
 * 原因：
 * <ol>
 *   <li>{@code MQEvent.publish()} 本身不抛异常（找不到 publisher 时 warn 并返回）</li>
 *   <li>{@link DomainEventPublisher} 是轻量桥接接口，调用方期望非侵入</li>
 *   <li>可靠投递由 outbox 模式保证（{@link io.ddd4j.mq.delivery.MQOutboxStore}
 *       + {@link io.ddd4j.mq.delivery.MQOutboxDispatcher}），不属于本类职责</li>
 * </ol>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
@Slf4j
public class MqDomainEventPublisher implements DomainEventPublisher {

    /**
     * 领域事件统一 tag，便于消费者批量订阅。
     */
    static final String DOMAIN_EVENT_TAG = "domain-event";

    private final MQEventSerialization serialization;

    /**
     * 使用 {@link JsonMQEventSerialization} 作为默认序列化器构造。
     */
    public MqDomainEventPublisher() {
        this(new JsonMQEventSerialization());
    }

    /**
     * 使用指定序列化器构造。
     *
     * @param serialization MQ 事件序列化器
     */
    public MqDomainEventPublisher(MQEventSerialization serialization) {
        this.serialization = Objects.requireNonNull(serialization, "serialization must not be null");
    }

    @Override
    public <ID extends EntityId> void publish(DomainEvent<ID> event) {
        if (Objects.isNull(event)) {
            return;
        }
        DomainEventCarrier carrier = toCarrier(event);
        log.info("Publishing domain event to MQ: type={}, eventId={}", event.getEventType(), event.getEventId());
        carrier.publish();
    }

    @Override
    public void publish(Object event) {
        if (event instanceof DomainEvent<?> domainEvent) {
            publish(domainEvent);
        } else {
            log.warn("MqDomainEventPublisher received non-DomainEvent object, skipped: type={}",
                    Objects.nonNull(event) ? event.getClass().getName() : "null");
        }
    }

    @Override
    public <ID extends EntityId> void publishAll(Collection<DomainEvent<ID>> events) {
        if (Objects.nonNull(events)) {
            events.forEach(this::publish);
        }
    }

    /**
     * 将领域事件转换为 {@link DomainEventCarrier}。
     *
     * @param event 领域事件
     * @return MQ 载体事件
     */
    DomainEventCarrier toCarrier(DomainEvent<?> event) {
        String payload = serialization.serialize(event);
        DomainEventCarrier carrier = new DomainEventCarrier(event.getClass().getName(), payload);
        // topic = eventType（事件类简单名称，如 "OrderCreatedEvent"）
        carrier.setTopic(event.getEventType().asString());
        // tag = 固定 "domain-event"，便于消费者统一批量订阅
        carrier.setTag(DOMAIN_EVENT_TAG);
        // 租户传递：优先领域事件的线程上下文
        carrier.setTenantId(ThreadContext.get(ContextConstants.TENANT_ID));
        // 消息 ID 复用领域事件 ID
        if (Objects.nonNull(event.getEventId())) {
            carrier.setMsgId(event.getEventId().asString());
        } else {
            carrier.setMsgId(UUID.randomUUID().toString());
        }
        return carrier;
    }
}
