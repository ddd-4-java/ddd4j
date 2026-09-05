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

package io.ddd4j.core.ddd.repository;

import io.ddd4j.core.ddd.model.AggregateRoot;
import java.io.Serializable;

/**
 * 事件溯源仓储接口（ddd4j 推荐用于 ES 场景）。
 * <p>
 *
 * <h3>与普通 {@link Repository} 的区别</h3>
 * <ul>
 *   <li><b>状态不落库</b>：聚合根的状态仅来自事件流，仓储只追加事件</li>
 *   <li><b>{@code add} 而非 save</b>：新建聚合根（add），更新事件流（update）</li>
 *   <li><b>乐观锁重试</b>：自动处理版本冲突，最多重试 3 次</li>
 *   <li><b>{@code read} 可指定版本</b>：支持从历史版本重建</li>
 * </ul>
 *
 * <h3>事件溯源工作流</h3>
 * <pre>{@code
 * // 1. 创建
 * Order order = repository.read(orderId).orElseThrow(); // 重建
 * order.place(...);                                    // 业务方法 → registerEvent
 * repository.update(order);                            // 追加事件到流
 *
 * // 2. 读取
 * Order order = repository.read(orderId);               // 从 EventStore 拉事件 → loadFromHistory
 * }</pre>
 *
 * @param <M>  聚合根类型
 * @param <ID> 聚合根标识类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public interface EventSourcingRepository<M extends AggregateRoot<ID>, ID extends Serializable> {
    M read(ID aggregateId);
    M read(ID aggregateId, int version);
    void add(M aggregate);
    void update(M aggregate);
}
