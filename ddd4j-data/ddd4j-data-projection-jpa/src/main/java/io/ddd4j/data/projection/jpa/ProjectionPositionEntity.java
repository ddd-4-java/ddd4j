package io.ddd4j.data.projection.jpa;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 投影位置 JPA 实体：落地 {@code io.ddd4j.core.cqrs.readmodel.ProjectionPosition}
 * 的持久化形态（core 的 {@code DefaultProjectionPosition} 为不可变值对象，不入库）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>{@code streamId} 为自然主键（String），即 core {@code ProjectionPosition#getStreamId()}
 *       的返回类型——也是 {@code io.ddd4j.data.projection.ProjectionHandler#getName()}
 *       约定的投影流 ID，天然按视图名隔离命名空间。</li>
 *   <li>{@code nextEventNumber} 为位置计数列（下一个待处理事件号，0-based），语义同 core
 *       契约；<b>刻意不加 JPA {@code @Version} 乐观锁</b>（non-versionable）——推进与重置
 *       均走 {@link SpringDataProjectionPositionRepository} 的显式更新 SQL，
 *       避免实体托管态与数据库态的版本漂移。</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see SpringDataProjectionPositionRepository
 * @see JpaProjectionPositionRepository
 * @since 2.0.x
 */
@Entity
@Table(name = "ddd4j_projection_position")
public class ProjectionPositionEntity {

    /** 投影流 ID（视图唯一标识，自然主键）。 */
    @Id
    @Column(name = "stream_id", nullable = false, length = 250, updatable = false)
    private String streamId;

    /** 下一个待处理事件号（0-based 位置计数，non-versionable）。 */
    @Column(name = "next_event_number", nullable = false)
    private long nextEventNumber;

    public ProjectionPositionEntity() {
    }

    public ProjectionPositionEntity(String streamId, long nextEventNumber) {
        this.streamId = streamId;
        this.nextEventNumber = nextEventNumber;
    }

    /**
     * 构造归零实体（对应 core {@code DefaultProjectionPosition#zero}）。
     *
     * @param streamId 投影流 ID
     * @return 位置为 0 的实体
     */
    public static ProjectionPositionEntity zero(String streamId) {
        return new ProjectionPositionEntity(streamId, 0L);
    }

    public String getStreamId() {
        return streamId;
    }

    public void setStreamId(String streamId) {
        this.streamId = streamId;
    }

    public long getNextEventNumber() {
        return nextEventNumber;
    }

    public void setNextEventNumber(long nextEventNumber) {
        this.nextEventNumber = nextEventNumber;
    }
}
