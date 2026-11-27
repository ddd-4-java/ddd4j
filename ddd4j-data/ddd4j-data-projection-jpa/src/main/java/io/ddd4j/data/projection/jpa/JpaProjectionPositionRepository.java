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
package io.ddd4j.data.projection.jpa;

import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于 Spring Data JPA 的 {@link ProjectionPositionRepository} 实现（core SPI，
 * 位于 {@code io.ddd4j.core.cqrs.readmodel}——本模块<b>不重定义任何投影契约</b>，
 * 与 {@code ddd4j-data-projection} 的零重定义约定一致）。
 *
 * <p>适用于任何使用 Hibernate/JPA 的 Spring 系运行时（WebMVC/WebFlux 等）；Quarkus 请用
 * 后续 {@code ddd4j-data-projection-panache}。本类只做「core SPI 语义 ↔ JPA 持久化原语」
 * 的适配组装：实体与不可变值对象 {@link DefaultProjectionPosition} 互转、缺行归零、
 * upsert 语义，持久化原语全部由 {@link SpringDataProjectionPositionRepository} 承担。
 *
 * <p>结构上不把 core SPI 直接塞进 Spring Data 仓储接口：{@code JpaRepository#findAll}
 * 固定返回 {@code List<实体>}，与 SPI 的 {@code List<ProjectionPosition>} 泛型不可协变
 * （同一方法名无法共存），故按 {@code ddd4j-data-event-store-jpa} 的
 * 「SpringData 仓储 + Jpa 适配器」两件套拆分（ADR-0005 同款分层）。
 *
 * <p>事务由 {@code @Transactional} 声明式管理（Spring 运行时由 {@code JpaTransactionManager}
 * 装配；集成方已有事务时本层加入即可，无需额外包装门面）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see SpringDataProjectionPositionRepository
 * @see ProjectionPositionEntity
 * @since 2.0.x
 */
@Repository
public class JpaProjectionPositionRepository implements ProjectionPositionRepository {

    /** \u5728 Spring \u4e8b\u52a1\u4e2d\u8bfb\u53d6\u548c\u4fdd\u5b58\u4f4d\u70b9\u7684\u6301\u4e45\u5316\u4ed3\u50a8\u3002 */
    private final SpringDataProjectionPositionRepository repository;

    /**
     * 创建基于 Spring Data JPA 的投影位置仓库。
     *
     * @param repository Spring Data JPA 仓库
     */
    public JpaProjectionPositionRepository(SpringDataProjectionPositionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /**
     * 查询指定事件流的投影检查点。
     *
     * <p>\u5b9e\u4f53\u8f6c\u4e0d\u53ef\u53d8\u503c\u5bf9\u8c61\u8fd4\u56de\uff08\u4e0d\u6cc4\u6f0f\u6301\u4e45\u5316\u5b9e\u4f53\u5230\u9886\u57df\u5c42\uff09\u3002
     *
     * <p>JPA 实现调用 <code>repository.findById(streamId)</code>。</p>
     *
     * @param streamId 投影流 ID
     * @return 与给定流标识对应的投影检查点
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectionPosition> findByStreamId(String streamId) {
        return repository.findById(streamId)
                .map(entity -> (ProjectionPosition) new DefaultProjectionPosition(
                        entity.getStreamId(), entity.getNextEventNumber()));
    }

    /**
     * 查询存储中符合条件的记录。
     *
     * <p>JPA 实现调用 <code>repository.findAll().stream()</code>。</p>
     *
     * @return 满足条件的结果列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<ProjectionPosition> findAll() {
        return repository.findAll().stream()
                .map(entity -> (ProjectionPosition) new DefaultProjectionPosition(
                        entity.getStreamId(), entity.getNextEventNumber()))
                .toList();
    }

    /**
     * 在持久化存储中写入指定事件流的投影检查点。
     *
     * <p>upsert \u8bed\u4e49\uff1a\u884c\u4e0d\u5b58\u5728\u5219\u63d2\u5165\uff08{@code ProjectionDispatcher} \u9996\u6b21\u63a8\u8fdb
     *  \u300c\u7f3a\u884c\u6309 0 \u63a8\u8fdb\u518d save\u300d\u7684\u8def\u5f84\u4f9d\u8d56\u6b64\u884c\u4e3a\uff09\uff0c\u5df2\u5b58\u5728\u5219\u539f\u4f4d\u66f4\u65b0\u4f4d\u7f6e\u8ba1\u6570\u3002
     *
     * <p>JPA 实现调用 <code>repository.findById(position.getStreamId())</code>。</p>
     *
     * @param position 投影位置
     * @return 持久化后的不可变位置值
     */
    @Override
    @Transactional
    public ProjectionPosition save(ProjectionPosition position) {
        Objects.requireNonNull(position, "position must not be null");
        ProjectionPositionEntity entity = repository.findById(position.getStreamId())
                .orElseGet(() -> ProjectionPositionEntity.zero(position.getStreamId()));
        entity.setNextEventNumber(position.getNextEventNumber());
        repository.saveAndFlush(entity);
        return new DefaultProjectionPosition(position.getStreamId(), position.getNextEventNumber());
    }

    /**
     * 删除指定事件流的投影检查点。
     *
     * <p>JPA 实现调用 <code>repository.deleteById(streamId)</code>。</p>
     *
     * @param streamId 投影流 ID
     */
    @Override
    @Transactional
    public void deleteByStreamId(String streamId) {
        repository.deleteById(streamId);
    }

    /**
     * 将指定流的投影检查点重置为零。
     *
     * <p>\u8d70\u6570\u636e\u5e93\u7aef\u539f\u5b50 UPDATE\uff1b\u884c\u4e0d\u5b58\u5728\u65f6\u6309 core
     *  {@code InMemoryProjectionPositionRepository} \u540c\u6b3e\u8bed\u4e49\u63d2\u5165 0 \u4f4d\u7f6e
     *  \uff08\u4fdd\u8bc1 reset \u540e {@link #findByStreamId} \u53ef\u8bfb\u5230 0 \u800c\u975e\u7a7a\uff09\u3002
     *
     * <p>JPA 实现调用 <code>repository.resetToZero(streamId)</code>。</p>
     *
     * @param streamId 投影流 ID
     */
    @Override
    @Transactional
    public void resetToZero(String streamId) {
        if (repository.resetToZero(streamId) == 0) {
            repository.saveAndFlush(ProjectionPositionEntity.zero(streamId));
        }
    }

    /**
     * 数据库端原子自增指定流的位置计数并读回新值（本模块扩展，非 core SPI 方法；
     * 供追赶消费等需要「自增＋确认」原子性的场景使用）。
     *
     * @param streamId 投影流 ID
     * @param delta    自增量（正数）
     * @return 自增后的最新位置计数
     * @throws IllegalArgumentException 流不存在时（须先 {@link #save(ProjectionPosition)} 落行）
     * @throws IllegalStateException 更新成功后读回位置行失败
     */
    @Transactional
    public long incrementBy(String streamId, long delta) {
        if (repository.incrementBy(streamId, delta) == 0) {
            throw new IllegalArgumentException("projection position not found: " + streamId);
        }
        return repository.findById(streamId)
                .map(ProjectionPositionEntity::getNextEventNumber)
                .orElseThrow(() -> new IllegalStateException(
                        "projection position disappeared after increment: " + streamId));
    }
}
