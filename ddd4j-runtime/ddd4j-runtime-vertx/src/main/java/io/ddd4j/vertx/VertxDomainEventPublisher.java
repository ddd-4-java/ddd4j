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
package io.ddd4j.vertx;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.event.EntityId;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 将领域事件发布到 Vert.x 本地 EventBus。
 */
@Slf4j
public final class VertxDomainEventPublisher implements DomainEventPublisher {

    /**
     * 本地领域事件总线地址。
     */
    public static final String ADDRESS = "ddd4j.domain.events";
    /**
     * 用于命名本地事件总线编解码器的前缀。
     */
    private static final String CODEC_PREFIX = "ddd4j.local-event.";
    /**
     * 当前框架组件共享的 Vert.x 实例。
     */
    private final Vertx vertx;
    /**
     * 已为本地事件类型安装的编解码器名称。
     */
    private final Map<Class<?>, String> codecNames = new ConcurrentHashMap<>();

    /**
     * 使用指定 Vertx 实例创建事件发布器。
     *
     * @param vertx Vertx 实例
     */
    public VertxDomainEventPublisher(Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx, "vertx must not be null");
    }

    /**
     * 将传入的事件交给当前发布通道。
     *
     * <p>Vert.x 实现调用 <code>publish((Object)event)</code>。</p>
     *
     * @param <ID>  实体标识类型
     * @param event 领域事件
     */
    @Override
    public <ID extends EntityId> void publish(DomainEvent<ID> event) {
        publish((Object) event);
    }

    /**
     * 将传入的事件交给当前发布通道。
     *
     * <p>Vert.x 实现调用 <code>log.debug("Publishing Vert.x event: {}", event.getClass().getName())</code>。</p>
     *
     * @param event 任意事件对象
     */
    @Override
    public void publish(Object event) {
        if (Objects.nonNull(event)) {
            log.debug("Publishing Vert.x event: {}", event.getClass().getName());
            String codecName = codecNames.computeIfAbsent(event.getClass(), this::registerLocalCodec);
            vertx.eventBus().publish(ADDRESS, event, new DeliveryOptions().setCodecName(codecName));
        }
    }

    /**
     * 为运行时事件类型按需登记本地总线编解码器。
     *
     * @param eventType 待登记本地编解码器的事件类型
     * @return 当前事件类型可使用的本地编解码器名称
     */
    private String registerLocalCodec(Class<?> eventType) {
        String codecName = CODEC_PREFIX + eventType.getName();
        vertx.eventBus().registerCodec(new LocalEventMessageCodec(codecName));
        return codecName;
    }
}
