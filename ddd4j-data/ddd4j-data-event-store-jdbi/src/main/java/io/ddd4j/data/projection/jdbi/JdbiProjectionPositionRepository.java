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
package io.ddd4j.data.projection.jdbi;

import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import org.jdbi.v3.core.Jdbi;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于 JDBI 的 {@link ProjectionPositionRepository} 实现（core SPI，位于
 * {@code io.ddd4j.core.cqrs.readmodel}——本模块<b>不重定义任何投影契约</b>，
 * 与 {@code ddd4j-data-projection} 的零重定义约定一致）。
 *
 * <p>SQL-first：全部读写以手写 SQL 经 jdbi3-core 的 Statement/Handle 原语执行
 * （无注解 SQL Object），适用于 Javalin／Vert.x 等轻量运行时；Spring 系运行时请用
 * {@code ddd4j-data-projection-jpa}，Quarkus 请用 {@code ddd4j-data-projection-panache}，
 * 响应式请用 {@code ddd4j-data-projection-r2dbc}。
 *
 * <h2>集成方装配</h2>
 * <p>本类为纯类（零容器注解、非容器托管）：Javalin／Vert.x 集成方在应用装配代码中
 * 手动 {@code new JdbiProjectionPositionRepository(jdbi)} 并自行管理其生命周期——
 * {@code Jdbi} 实例（可包连接池 DataSource）由集成方提供。生命周期不入 SPI
 * （ADR-0003）：连接资源由 {@code Jdbi} 底层 DataSource 管理，每方法一次
 * {@link Jdbi#withHandle}／{@link Jdbi#useHandle}，无隐式 open/close。
 *
 * <h2>表契约</h2>
 * <p>目标表 {@code ddd4j_projection_position} 与 -jpa/-panache 模块同构
 * （{@code stream_id VARCHAR(250)} 自然主键＋{@code next_event_number BIGINT}），
 * DDL 由集成方负责建表。save 的 upsert 用 H2 原生 {@code MERGE INTO ... KEY}
 * 单语句原子完成——实证（H2 2.4.240 Shell）：常规模式下
 * {@code INSERT ... ON CONFLICT (stream_id) DO UPDATE} 抛
 * {@code JdbcSQLSyntaxErrorException [42000-240]}，MERGE INTO 则两次执行后
 * 原位更新（1 行、计数原位变化），故选 MERGE INTO。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see ProjectionPosition
 * @see DefaultProjectionPosition
 * @since 2.0.x
 */
public class JdbiProjectionPositionRepository implements ProjectionPositionRepository {

    /**
     * 行读取列集（与表契约一致：自然主键＋位置计数）。
     */
    private static final String SELECT_COLUMNS = "select stream_id, next_event_number from ddd4j_projection_position";

    /**
     * 单语句原子 upsert（H2 语法；PostgreSQL 等方言运行时由集成方按需替换为
     * {@code INSERT ... ON CONFLICT (stream_id) DO UPDATE}）。
     */
    private static final String MERGE_SQL = "merge into ddd4j_projection_position "
            + "(stream_id, next_event_number) key (stream_id) values (:streamId, :next)";

    /**
     * 负责获取 JDBC 连接并执行 SQL 操作的访问入口。
     */
    private final Jdbi jdbi;

    /**
     * 创建 JDBI 投影位置仓储。
     *
     * @param jdbi JDBI 实例（集成方装配，可包连接池 DataSource）
     */
    public JdbiProjectionPositionRepository(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi must not be null");
    }

    /**
     * 查询指定事件流的投影检查点。
     *
     * <p>\u884c\u91cd\u5efa\u4e3a\u4e0d\u53ef\u53d8\u503c\u5bf9\u8c61 {@link DefaultProjectionPosition}\uff08\u4e0d\u66b4\u9732\u539f\u59cb\u884c\uff09\uff0c
     * \u7f3a\u884c\u8fd4\u56de {@link Optional#empty()}\u3002
     *
     * @param streamId 投影流 ID
     * @return 与给定流标识对应的投影检查点
     */
    @Override
    public Optional<ProjectionPosition> findByStreamId(String streamId) {
        return jdbi.withHandle(handle -> handle.createQuery(
                        SELECT_COLUMNS + " where stream_id = :id")
                .bind("id", streamId)
                .map((rs, ctx) -> (ProjectionPosition) new DefaultProjectionPosition(
                        rs.getString("stream_id"), rs.getLong("next_event_number")))
                .findOne());
    }

    /**
     * 查询存储中符合条件的记录。
     *
     * @return 满足条件的结果列表
     */
    @Override
    public List<ProjectionPosition> findAll() {
        return jdbi.withHandle(handle -> handle.createQuery(SELECT_COLUMNS)
                .map((rs, ctx) -> (ProjectionPosition) new DefaultProjectionPosition(
                        rs.getString("stream_id"), rs.getLong("next_event_number")))
                .list());
    }

    /**
     * 在持久化存储中写入指定事件流的投影检查点。
     *
     * <p>upsert \u8bed\u4e49\uff1a\u884c\u4e0d\u5b58\u5728\u5219\u63d2\u5165\uff08{@code ProjectionDispatcher} \u9996\u6b21\u63a8\u8fdb
     * \u300c\u7f3a\u884c\u6309 0 \u63a8\u8fdb\u518d save\u300d\u7684\u8def\u5f84\u4f9d\u8d56\u6b64\u884c\u4e3a\uff09\uff0c\u5df2\u5b58\u5728\u5219\u539f\u4f4d\u66f4\u65b0\u4f4d\u7f6e\u8ba1\u6570\u2014\u2014
     * \u5355\u6761 MERGE INTO \u539f\u5b50\u5b8c\u6210\uff08\u65e0\u300c\u5148\u67e5\u540e\u5199\u300d\u7ade\u6001\u7a97\u53e3\uff09\u3002
     *
     * <p>Jdbi 实现调用 <code>upsert(position.getStreamId(), position.getNextEventNumber())</code>。</p>
     *
     * @param position 投影位置
     * @return 持久化后的不可变位置值
     */
    @Override
    public ProjectionPosition save(ProjectionPosition position) {
        Objects.requireNonNull(position, "position must not be null");
        return upsert(position.getStreamId(), position.getNextEventNumber());
    }

    /**
     * 删除指定事件流的投影检查点。
     *
     * <p>\u7f3a\u884c\u65f6\u5220\u9664 0 \u884c\u3001\u9759\u9ed8\u6210\u529f\uff08\u65e0\u5f02\u5e38\u5951\u7ea6\uff09\uff1b\u4e0e -jpa \u6a21\u5757\u5dee\u5f02\u8bf4\u660e\uff1a
     * \u672c\u5b9e\u73b0\u4e0d\u533a\u5206\u300c\u5220\u5b58\u5728\u884c\u300d\u4e0e\u300c\u5220\u7f3a\u884c\u300d\uff0c\u4e24\u8005\u7686\u65e0\u5f02\u5e38\u3002
     *
     * @param streamId 投影流 ID
     */
    @Override
    public void deleteByStreamId(String streamId) {
        jdbi.useHandle(handle -> handle.createUpdate(
                        "delete from ddd4j_projection_position where stream_id = :id")
                .bind("id", streamId)
                .execute());
    }

    /**
     * 将指定流的投影检查点重置为零。
     *
     * <p>\u6309 core {@code InMemoryProjectionPositionRepository} \u540c\u6b3e\u8bed\u4e49\u5b9e\u73b0\uff1a
     * {@code save(zero(streamId))}\u2014\u2014\u7f3a\u884c reset \u540e\u63d2\u5165\u96f6\u4f4d\u884c\uff08\u4fdd\u8bc1 reset \u540e
     * {@link #findByStreamId} \u53ef\u8bfb\u5230 0 \u800c\u975e\u7a7a\uff09\uff0cMERGE INTO \u7684 upsert \u8bed\u4e49
     * \u5929\u7136\u8986\u76d6\u4e24\u5206\u652f\u3002
     *
     * <p>Jdbi 实现调用 <code>upsert(streamId, 0L)</code>。</p>
     *
     * @param streamId 投影流 ID
     */
    @Override
    public void resetToZero(String streamId) {
        upsert(streamId, 0L);
    }

    /**
     * 单语句原子落位（save／resetToZero 共用）。
     *
     * @param streamId        投影流 ID（自然主键）
     * @param nextEventNumber 位置计数
     * @return 持久化后的不可变位置值
     */
    private ProjectionPosition upsert(String streamId, long nextEventNumber) {
        jdbi.useHandle(handle -> handle.createUpdate(MERGE_SQL)
                .bind("streamId", streamId)
                .bind("next", nextEventNumber)
                .execute());
        return new DefaultProjectionPosition(streamId, nextEventNumber);
    }
}
