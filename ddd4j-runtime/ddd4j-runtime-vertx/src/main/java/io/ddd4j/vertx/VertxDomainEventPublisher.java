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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 将领域事件发布到 Vert.x 本地 EventBus。
 */
public final class VertxDomainEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(VertxDomainEventPublisher.class);

    public static final String ADDRESS = "ddd4j.domain.events";
    private static final String CODEC_PREFIX = "ddd4j.local-event.";
    private final Vertx vertx;
    private final Map<Class<?>, String> codecNames = new ConcurrentHashMap<>();

    public VertxDomainEventPublisher(Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx, "vertx must not be null");
    }

    @Override
    public <ID extends EntityId> void publish(DomainEvent<ID> event) {
        publish((Object) event);
    }

    @Override
    public void publish(Object event) {
        if (Objects.nonNull(event)) {
            log.debug("Publishing Vert.x event: {}", event.getClass().getName());
            String codecName = codecNames.computeIfAbsent(event.getClass(), this::registerLocalCodec);
            vertx.eventBus().publish(ADDRESS, event, new DeliveryOptions().setCodecName(codecName));
        }
    }

    private String registerLocalCodec(Class<?> eventType) {
        String codecName = CODEC_PREFIX + eventType.getName();
        vertx.eventBus().registerCodec(new LocalEventMessageCodec(codecName));
        return codecName;
    }
}
