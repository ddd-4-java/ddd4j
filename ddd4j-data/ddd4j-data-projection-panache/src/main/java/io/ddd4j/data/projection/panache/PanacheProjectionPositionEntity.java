package io.ddd4j.data.projection.panache;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 投影位置 Quarkus Panache 实体：以 active record 风格落地
 * {@code io.ddd4j.core.cqrs.readmodel.ProjectionPosition}（core 的
 * {@code DefaultProjectionPosition} 为不可变值对象，不入库）。
 *
 * <p>表结构与 {@code ddd4j-data-projection-jpa} 的 {@code ProjectionPositionEntity}
 * 完全一致（{@code ddd4j_projection_position} 表，{@code stream_id} 自然主键即唯一键），
 * 同一数据库可被两种实现的集成方分别使用；勿在同一应用混用（non-versionable 语义
 * 以单实现内自洽为准）。
 *
 * <p><b>公有字段风格是 Panache 的刻意约定</b>：Quarkus Panache active record 模式
 * （继承 {@link PanacheEntityBase}）以公有字段直接承载列映射，省去 getter/setter 样板；
 * 与 -jpa 模块的私有字段风格并存是有意的（各随其运行时惯例），勿以封装规范统一改写。
 *
 * <p>设计要点（同 -jpa 模块）：
 * <ul>
 *   <li>{@code streamId} 为自然主键（String），即 core {@code ProjectionPosition#getStreamId()}
 *       的返回类型——也是 {@code io.ddd4j.data.projection.ProjectionHandler#getName()}
 *       约定的投影流 ID，天然按视图名隔离命名空间。</li>
 *   <li>{@code nextEventNumber} 为位置计数列（下一个待处理事件号，0-based），语义同 core
 *       契约；<b>刻意不加 JPA {@code @Version} 乐观锁</b>（non-versionable）——推进与重置
 *       均走 {@link #upsert} 的显式读写路径，避免实体托管态与数据库态的版本漂移。</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see QuarkusProjectionPositionRepository
 * @since 2.0.x
 */
@Entity
@Table(name = "ddd4j_projection_position")
public class PanacheProjectionPositionEntity extends PanacheEntityBase {

    /** 投影流 ID（视图唯一标识，自然主键即唯一键）。 */
    @Id
    @Column(name = "stream_id", nullable = false, length = 250, updatable = false)
    public String streamId;

    /** 下一个待处理事件号（0-based 位置计数，non-versionable）。 */
    @Column(name = "next_event_number", nullable = false)
    public long nextEventNumber;

    /**
     * upsert：行不存在则插入，已存在则原位更新位置计数（同一事务内完成，
     * 须由调用方保证活动事务——{@code jakarta.transaction.Transactional}）。
     *
     * @param streamId        投影流 ID
     * @param nextEventNumber 新的位置计数
     * @return 落库后的实体（托管态）
     */
    public static PanacheProjectionPositionEntity upsert(String streamId, long nextEventNumber) {
        // 显式局部变量类型：继承自 PanacheEntityBase 的静态泛型 find 在链式调用下
        // javac 推断兜底为 PanacheEntityBase（-panache 事件存储模块同款经验）
        PanacheProjectionPositionEntity entity = find("streamId", streamId).firstResult();
        if (entity == null) {
            entity = new PanacheProjectionPositionEntity();
            entity.streamId = streamId;
        }
        entity.nextEventNumber = nextEventNumber;
        entity.persist();
        return entity;
    }

    /**
     * 把指定流的位置计数重置为 0（重新拉取全量事件）；行不存在时按 core
     * {@code InMemoryProjectionPositionRepository} 同款语义插入 0 位置
     * （保证 reset 后可读到 0 而非空）。
     *
     * @param streamId 投影流 ID
     */
    public static void resetToZero(String streamId) {
        upsert(streamId, 0L);
    }
}
