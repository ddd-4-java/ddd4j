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

import java.util.List;

/**
 * 事件存储 SPI 接口（CQRS 写侧核心契约）。
 *
 * <p>定义事件溯源场景下追加事件、按聚合读取事件、全局分页读取三大操作。
 * 框架提供 {@link InMemoryEventStore} 默认实现，业务方可替换为持久化实现
 * （JDBC / EventStoreDB / Kafka 等）。
 *
 * <h3>乐观并发控制</h3>
 * <p>{@link #append} 通过 {@code expectedVersion} 实现乐观锁：
 * 若存储中当前版本与期望版本不一致，抛出 {@link IllegalStateException}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public interface EventStore {

    /**
     * 追加事件到指定聚合的事件流。
     *
     * @param aggregateId     聚合根标识
     * @param events          待追加的事件载荷列表
     * @param expectedVersion 期望的当前版本号（乐观锁）
     * @throws IllegalStateException 版本冲突时抛出
     */
    void append(String aggregateId, List<Object> events, long expectedVersion);

    /**
     * 读取指定聚合的全部事件（按版本升序）。
     *
     * @param aggregateId 聚合根标识
     * @return 该聚合的存储事件列表；聚合不存在时返回空列表
     */
    List<StoredEvent> read(String aggregateId);

    /**
     * 全局分页读取事件（按全局 position 升序）。
     *
     * @param fromPosition 起始 position（包含）
     * @param limit        最大返回条数
     * @return 满足条件的存储事件列表
     */
    List<StoredEvent> readAll(long fromPosition, int limit);
}
