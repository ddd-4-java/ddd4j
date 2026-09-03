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
package io.ddd4j.core.ddd.event;

import java.util.Collection;

/**
 * 空操作的领域事件发布者（丢弃所有事件）。
 *
 * <p> canonical Noop 实现，供以下场景直接使用，避免各业务/示例工程重复编写：
 * <ul>
 *   <li>单元测试 / 集成测试中不需要事件派发时；</li>
 *   <li>未接入任何事件总线的最小运行环境；</li>
 *   <li>作为 {@link DomainEventPublisher} 的缺省兜底。</li>
 * </ul>
 *
 * <p>与 {@code io.ddd4j.core.cqrs.readmodel.NoopEventChunkReader} 同为 core 内置的
 * Noop 系列默认实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class NoopDomainEventPublisher implements DomainEventPublisher {

    /**
     * 共享单例（无状态，可全局复用）。
     */
    public static final NoopDomainEventPublisher INSTANCE = new NoopDomainEventPublisher();

    @Override
    public <ID extends EntityId> void publish(DomainEvent<ID> event) {
        // 丢弃事件
    }

    @Override
    public <ID extends EntityId> void publishAll(Collection<DomainEvent<ID>> events) {
        // 丢弃事件
    }
}
