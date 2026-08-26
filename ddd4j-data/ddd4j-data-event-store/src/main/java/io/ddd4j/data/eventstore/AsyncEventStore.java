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
package io.ddd4j.data.eventstore;

import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 事件存储 SPI——响应式轨道（ADR-0005 单轨决策，见
 * {@code docs/adr/0005-event-store-spi.md}）。
 *
 * <p>与 {@link EventStore} 同四方法语义的 Project Reactor 版本：append 以
 * {@link Mono} 表达「完成或失败」，读侧以 {@link Flux} 流式输出。供 WebFlux／
 * Vert.x 等响应式运行时在全链路非阻塞地访问事件存储。
 *
 * <p>与 esc-api CompletableFuture 双轨的对照（ADR-0005 摒弃项）：esc-api 为
 * {@code EventStoreAsync}／{@code EventStore} 各复制约 20 个 CompletableFuture
 * 签名，双轨全量复制导致漂移风险高，且 {@code DelegatingAsyncEventStore}
 * 以线程池包同步实现「异步名不副实」。ddd4j 的取舍：<b>单轨响应式</b>——异步
 * 扩展只有本接口这一份 Reactor 签名，不做同步签名的 CompletableFuture 复刻；
 * 需要阻塞语义的运行时直接用 {@link EventStore}。
 *
 * <h3>乐观锁</h3>
 * <p>语义与 {@link EventStore} 完全一致：append 校验 {@code expectedVersion}
 * （期望的流当前版本号，空流为 0），不一致时以
 * {@link AggregateVersionConflictException} 错误信号终止（{@link Mono#error}）。
 * 实现须保证 append 的版本校验与写入在同一数据库事务内原子完成。
 *
 * <h3>实现</h3>
 * <ul>
 *   <li>{@code ddd4j-data-event-store-r2dbc}：纯 {@code io.r2dbc.spi} 的
 *       {@code R2dbcEventStore}（真响应式事务，同时服务 WebFlux 与 Vert.x）</li>
 * </ul>
 * 同步轨道实现（JPA／Panache／JDBI）见 {@link EventStore}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see EventStore
 * @since 2.0.x
 */
public interface AsyncEventStore {

    /**
     * 追加事件到聚合流。
     *
     * <p>语义与 {@link EventStore} 的 append 一致：实现须在同一事务内完成乐观锁校验
     * （{@code expectedVersion} 与流实际
     * 版本一致才写入）与逐条持久化，并为每个事件分配全局递增 {@code position}；
     * 任一环节失败则整体回滚，不留半截流。
     *
     * @param aggregateType   聚合类型
     * @param aggregateId     聚合 ID
     * @param events          要追加的事件流（实现会先行物化，订阅一次即完成追加）
     * @param expectedVersion 期望的当前版本号（乐观锁，空流为 0）
     * @return 完成信号；版本冲突时以 {@link AggregateVersionConflictException} 失败
     */
    Mono<Void> append(String aggregateType, AggregateRootId aggregateId,
                      Flux<? extends DomainEvent<?>> events, long expectedVersion);

    /**
     * 读取聚合全部事件。
     *
     * <p>流不存在时返回空 {@link Flux}（读侧轻量状态探测思想，与
     * {@link EventStore} 一致）。
     *
     * @param aggregateType 聚合类型
     * @param aggregateId   聚合 ID
     * @return 按版本升序的持久化事件流；无事件时为空流
     */
    Flux<StoredEvent> read(String aggregateType, AggregateRootId aggregateId);

    /**
     * 读取指定版本区间的事件。
     *
     * @param aggregateType 聚合类型
     * @param aggregateId   聚合 ID
     * @param fromVersion   起始版本号（含）
     * @param toVersion     结束版本号（含）
     * @return 版本区间内的持久化事件流，按版本升序
     */
    Flux<StoredEvent> read(String aggregateType, AggregateRootId aggregateId,
                           long fromVersion, long toVersion);

    /**
     * 读取全局事件流（用于 projection）。
     *
     * <p>{@code position} 是跨所有聚合流全局递增的序号，投影以「上次处理到的
     * position」为断线续传位点，循环调用本方法直至读取数小于 {@code limit}。
     * 实现应把 {@code limit} 下推为数据库分页，而非内存截断。
     *
     * @param fromPosition 起始 position（含）
     * @param limit        最大读取数量
     * @return position 升序的持久化事件流
     */
    Flux<StoredEvent> readAll(long fromPosition, int limit);
}
