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
package io.ddd4j.data.event.store.jpa;

import java.util.List;

/**
 * 事件存储仓储接口——封装 JPA 持久化原语。
 *
 * <p>隔离 {@link JpaEventStore} 与 EntityManager 细节，保持 EventStore 语义层纯净。
 * 本接口仅定义数据访问操作，不含乐观锁或序列化逻辑。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public interface JpaStoredEventRepository {

    /**
     * 查询指定聚合的当前最大版本号。
     *
     * @param aggregateId 聚合根标识
     * @return 当前最大版本号；聚合不存在时返回 0
     */
    long findCurrentVersion(String aggregateId);

    /**
     * 按版本升序查询指定聚合的全部事件。
     *
     * @param aggregateId 聚合根标识
     * @return 事件实体列表（版本升序）
     */
    List<StoredEventEntity> findByAggregateIdOrderByVersionAsc(String aggregateId);

    /**
     * 按全局 position 升序分页查询事件。
     *
     * @param fromPosition 起始 position（包含）
     * @param limit        最大返回条数
     * @return 事件实体列表（position 升序）
     */
    List<StoredEventEntity> findByPositionGreaterThanEqualOrderByPositionAsc(long fromPosition, int limit);

    /**
     * 持久化事件实体。
     *
     * @param entity 事件实体
     */
    void save(StoredEventEntity entity);

    /**
     * 生成下一个全局 position（原子操作）。
     *
     * <p>使用 {@code SELECT COALESCE(MAX(position), 0) + 1} 策略。
     * 在高并发场景下，建议使用数据库序列以避免争用。
     *
     * @return 下一个可用的全局 position
     */
    long nextPosition();
}
