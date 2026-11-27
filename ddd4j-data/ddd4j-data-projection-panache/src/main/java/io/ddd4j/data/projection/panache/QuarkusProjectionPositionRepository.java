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
package io.ddd4j.data.projection.panache;

import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import javax.enterprise.context.ApplicationScoped;
import javax.transaction.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于 Quarkus Hibernate ORM Panache 的 {@link ProjectionPositionRepository} 实现
 * （core SPI，位于 {@code io.ddd4j.core.cqrs.readmodel}——本模块<b>不重定义任何投影契约</b>，
 * 与 {@code ddd4j-data-projection} 的零重定义约定一致）。
 *
 * <p>适用于 Quarkus 1.x 运行时；Spring 系运行时请用 {@code ddd4j-data-projection-jpa}。
 * 本类只做「core SPI 语义 ↔ Panache 持久化原语」的适配组装：active record 实体
 * （{@link PanacheProjectionPositionEntity}）承载列映射与静态查询原语，值对象映射
 * （实体 ↔ 不可变 {@link DefaultProjectionPosition}）、缺行插入零位、upsert 语义在本层完成。
 *
 * <p><b>不注入实体</b>：Panache active record 的持久化原语是实体类上的静态方法
 * （{@code find/listAll/persist/delete}），实体非 CDI Bean，ArC 无从注入——与
 * {@code PanacheEventStore}（-panache 事件存储模块）同款：适配器直接静态委托，
 * 构造器无协作者Bean，故无注入参数。
 *
 * <p>生命周期不入 SPI（ADR-0003）：写路径的持久化原语须活动事务，由
 * {@code javax.transaction.Transactional}（Quarkus Narayana JTA，勿与 Spring 的
 * 同名注解混用）声明式管理；读路径无需事务包装。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see PanacheProjectionPositionEntity
 * @since 1.0.x
 */
@ApplicationScoped
public class QuarkusProjectionPositionRepository implements ProjectionPositionRepository {

    /**
     * 查询指定事件流的投影检查点。
     *
     * <p>\u5b9e\u4f53\u8f6c\u4e0d\u53ef\u53d8\u503c\u5bf9\u8c61\u8fd4\u56de\uff08\u4e0d\u6cc4\u6f0f\u6301\u4e45\u5316\u5b9e\u4f53\u5230\u9886\u57df\u5c42\uff09\u3002
     *
     * <p>Quarkus 实现调用 <code>PanacheProjectionPositionEntity.find("streamId", streamId).firstResult()</code>。</p>
     *
     * @param streamId 投影流 ID
     * @return 与给定流标识对应的投影检查点
     */
    @Override
    public Optional<ProjectionPosition> findByStreamId(String streamId) {
        // 显式局部变量类型：Panache 静态泛型 find 链式推断兜底问题（同实体 upsert 注）
        PanacheProjectionPositionEntity entity = PanacheProjectionPositionEntity
                .find("streamId", streamId).firstResult();
        return Optional.ofNullable(entity)
                .map(found -> (ProjectionPosition) new DefaultProjectionPosition(
                        found.streamId, found.nextEventNumber));
    }

    /**
     * 查询存储中符合条件的记录。
     *
     * <p>Quarkus 实现调用 <code>PanacheProjectionPositionEntity.listAll()</code>。</p>
     *
     * @return 满足条件的结果列表
     */
    @Override
    public List<ProjectionPosition> findAll() {
        List<PanacheProjectionPositionEntity> entities = PanacheProjectionPositionEntity.listAll();
        return entities.stream()
                .map(entity -> (ProjectionPosition) new DefaultProjectionPosition(
                        entity.streamId, entity.nextEventNumber))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 在持久化存储中写入指定事件流的投影检查点。
     *
     * <p>upsert \u8bed\u4e49\uff1a\u884c\u4e0d\u5b58\u5728\u5219\u63d2\u5165\uff08{@code ProjectionDispatcher} \u9996\u6b21\u63a8\u8fdb
     *  \u300c\u7f3a\u884c\u6309 0 \u63a8\u8fdb\u518d save\u300d\u7684\u8def\u5f84\u4f9d\u8d56\u6b64\u884c\u4e3a\uff09\uff0c\u5df2\u5b58\u5728\u5219\u539f\u4f4d\u66f4\u65b0\u4f4d\u7f6e\u8ba1\u6570\u3002
     *
     * <p>Quarkus 实现调用 <code>PanacheProjectionPositionEntity.upsert(position.getStreamId(), position.getNextEventNumber())</code>。</p>
     *
     * @param position 投影位置
     * @return 持久化后的不可变位置值
     */
    @Override
    @Transactional
    public ProjectionPosition save(ProjectionPosition position) {
        Objects.requireNonNull(position, "position must not be null");
        PanacheProjectionPositionEntity.upsert(position.getStreamId(), position.getNextEventNumber());
        return new DefaultProjectionPosition(position.getStreamId(), position.getNextEventNumber());
    }

    /**
     * 删除指定事件流的投影检查点。
     *
     * <p>Quarkus 实现调用 <code>PanacheProjectionPositionEntity.delete("streamId", streamId)</code>。</p>
     *
     * @param streamId 投影流 ID
     */
    @Override
    @Transactional
    public void deleteByStreamId(String streamId) {
        PanacheProjectionPositionEntity.delete("streamId", streamId);
    }

    /**
     * 将指定流的投影检查点重置为零。
     *
     * <p>\u59d4\u6258\u5b9e\u4f53 {@link PanacheProjectionPositionEntity#resetToZero}\uff1a\u884c\u5b58\u5728\u5219\u56de\u9000 0\uff0c
     *  \u4e0d\u5b58\u5728\u5219\u63d2\u5165\u96f6\u4f4d\u884c\uff08\u4fdd\u8bc1 reset \u540e {@link #findByStreamId} \u53ef\u8bfb\u5230 0 \u800c\u975e\u7a7a\uff0c
     *  \u4e0e core {@code InMemoryProjectionPositionRepository} \u540c\u6b3e\u8bed\u4e49\uff09\u3002
     *
     * <p>Quarkus 实现调用 <code>PanacheProjectionPositionEntity.resetToZero(streamId)</code>。</p>
     *
     * @param streamId 投影流 ID
     */
    @Override
    @Transactional
    public void resetToZero(String streamId) {
        PanacheProjectionPositionEntity.resetToZero(streamId);
    }
}
