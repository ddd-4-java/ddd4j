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

package io.ddd4j.core.cqrs.eventstore;

import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import java.util.List;

/**
 * 事件存储 SPI（ADR-0005）。
 *
 * <p>框架无关的四方法同步接口：API 形态对齐外部构件 esc-api（cqrs-4-java 参照系）
 * 的已验证语义，但完全独立实现（no code reuse）。与 esc-api 的关键差异见
 * docs/reference/fuin-api-patterns/05-event-store.md「ddd4j 自研决策」节：
 * <ul>
 *   <li>流标识为 {@code (aggregateType, AggregateRootId)} 直接参数对，替代 {@code StreamId}</li>
 *   <li>无 {@code open()/close()} 生命周期——资源管理交各运行时容器（ADR-0003）</li>
 *   <li>无 {@code deleteStream}——删除走墓碑领域事件的统一追加路径</li>
 *   <li>无不带 {@code expectedVersion} 的追加重载——乐观锁不可选关闭</li>
 *   <li>{@code readAll} 按全局 {@code position} 读取，补齐 esc-api 缺失的全局顺序</li>
 * </ul>
 *
 * <h3>乐观锁</h3>
 * <p>append 时校验 {@code expectedVersion}（期望的流当前版本号，空流为 0），
 * 与实际版本不一致时抛 {@link AggregateVersionConflictException}。
 *
 * <h3>实现</h3>
 * <ul>
 *   <li>JPA：{@code ddd4j-data-event-store-jpa}</li>
 *   <li>Quarkus Panache：{@code ddd4j-data-event-store-panache}</li>
 *   <li>Javalin JDBI：{@code ddd4j-data-event-store-jdbi}</li>
 *   <li>响应式（Reactor 单轨）：{@code ddd4j-data-event-store-r2dbc} 走 {@code AsyncEventStore}</li>
 * </ul>
 * 生命周期不入 SPI：实现按各运行时容器惯例装配，无隐式 open。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public interface EventStore {
    void append(String aggregateType, AggregateRootId aggregateId, List<? extends DomainEvent<?>> events, long expectedVersion);
    List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId);
    List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId, long fromVersion, long toVersion);
    List<StoredEvent> readAll(long fromPosition, int limit);
}
