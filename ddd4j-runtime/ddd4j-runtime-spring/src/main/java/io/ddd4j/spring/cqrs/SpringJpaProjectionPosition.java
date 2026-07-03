package io.ddd4j.spring.cqrs;

import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * Spring JPA 投影位置实体。
 *
 * <p>对应数据库表 {@code SPRING_QRY_PROJECTION_POS}（可通过 {@code @Table} 改名）。
 * 持久化读侧视图的增量拉取偏移量，重启后从上次位置继续拉取。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Entity
@Table(name = "SPRING_QRY_PROJECTION_POS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SpringJpaProjectionPosition implements ProjectionPosition, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件流 ID */
    @Id
    @Column(name = "STREAM_ID", nullable = false, length = 250, updatable = false)
    private String streamId;

    /** 下一条待处理的事件序号 */
    @Column(name = "NEXT_EVENT_NUMBER", nullable = false, updatable = true)
    private long nextEventNumber;

    @Override
    public long getNextEventNumber() {
        return nextEventNumber;
    }

    @Override
    public ProjectionPosition withNextEventNumber(long nextEventNumber) {
        this.nextEventNumber = nextEventNumber;
        return this;
    }
}
