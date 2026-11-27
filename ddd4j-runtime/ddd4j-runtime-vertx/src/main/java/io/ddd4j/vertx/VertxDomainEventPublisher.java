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

    /** \u672c\u5730\u9886\u57df\u4e8b\u4ef6\u603b\u7ebf\u5730\u5740\u3002 */
    public static final String ADDRESS = "ddd4j.domain.events";
    /** \u7528\u4e8e\u547d\u540d\u672c\u5730\u4e8b\u4ef6\u603b\u7ebf\u7f16\u89e3\u7801\u5668\u7684\u524d\u7f00\u3002 */
    private static final String CODEC_PREFIX = "ddd4j.local-event.";
    /** \u5f53\u524d\u6846\u67b6\u7ec4\u4ef6\u5171\u4eab\u7684 Vert.x \u5b9e\u4f8b\u3002 */
    private final Vertx vertx;
    /** \u5df2\u4e3a\u672c\u5730\u4e8b\u4ef6\u7c7b\u578b\u5b89\u88c5\u7684\u7f16\u89e3\u7801\u5668\u540d\u79f0\u3002 */
    private final Map<Class<?>, String> codecNames = new ConcurrentHashMap<>();

    /**
     * \u4f7f\u7528\u6307\u5b9a Vertx \u5b9e\u4f8b\u521b\u5efa\u4e8b\u4ef6\u53d1\u5e03\u5668\u3002
     * @param vertx Vertx \u5b9e\u4f8b
     */
    public VertxDomainEventPublisher(Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx, "vertx must not be null");
    }

    /** {@inheritDoc} */
    @Override
    public <ID extends EntityId> void publish(DomainEvent<ID> event) {
        publish((Object) event);
    }

    /** {@inheritDoc} */
    @Override
    public void publish(Object event) {
        if (Objects.nonNull(event)) {
            log.debug("Publishing Vert.x event: {}", event.getClass().getName());
            String codecName = codecNames.computeIfAbsent(event.getClass(), this::registerLocalCodec);
            vertx.eventBus().publish(ADDRESS, event, new DeliveryOptions().setCodecName(codecName));
        }
    }

    /**
     * \u4e3a\u8fd0\u884c\u65f6\u4e8b\u4ef6\u7c7b\u578b\u6309\u9700\u767b\u8bb0\u672c\u5730\u603b\u7ebf\u7f16\u89e3\u7801\u5668\u3002
     * @param eventType \u5f85\u767b\u8bb0\u672c\u5730\u7f16\u89e3\u7801\u5668\u7684\u4e8b\u4ef6\u7c7b\u578b
     * @return \u5f53\u524d\u4e8b\u4ef6\u7c7b\u578b\u53ef\u4f7f\u7528\u7684\u672c\u5730\u7f16\u89e3\u7801\u5668\u540d\u79f0
     */
    private String registerLocalCodec(Class<?> eventType) {
        String codecName = CODEC_PREFIX + eventType.getName();
        vertx.eventBus().registerCodec(new LocalEventMessageCodec(codecName));
        return codecName;
    }
}
