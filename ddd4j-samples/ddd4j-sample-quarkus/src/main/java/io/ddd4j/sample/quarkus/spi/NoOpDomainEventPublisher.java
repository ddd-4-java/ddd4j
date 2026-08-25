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
package io.ddd4j.sample.quarkus.spi;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import io.ddd4j.core.ddd.event.EntityId;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Objects;

/**
 * 进程内领域事件发布者：No-Op 示例实现（仅打印）。
 *
 * <p>本类为 Quarkus 风格实现：
 * <ul>
 *   <li>标注 {@link ApplicationScoped}，由 CDI 容器管理生命周期。</li>
 *   <li>ddd4j-runtime-quarkus 在启动期会扫描所有 {@link DomainEventPublisher} Bean，
 *       自动注入到 {@code BaseContext} 的 {@code SpiKeys.DOMAIN_EVENT_PUBLISHER} key 下。</li>
 * </ul>
 *
 * <p>真实应用应基于 Guava EventBus / Reactor Sinks / Akka / 自研总线等实现。
 * 这里用最简化的方式演示 SPI 接口的契约与 CDI 注入流程。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ApplicationScoped
@Slf4j
public class NoOpDomainEventPublisher implements DomainEventPublisher {

    @Override
    public <ID extends EntityId> void publish(DomainEvent<ID> event) {
        if (Objects.isNull(event)) {
            return;
        }
        log.info("[DomainEvent] {} -> {}", event.getClass().getSimpleName(), event.source());
    }

    @Override
    public <ID extends EntityId> void publishAll(Collection<DomainEvent<ID>> events) {
        if (Objects.nonNull(events)) {
            events.forEach(this::publish);
        }
    }
}
