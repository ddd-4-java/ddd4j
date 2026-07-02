package io.ddd4j.quarkus.cqrs;

import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
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
 * Quarkus Hibernate Panache 投影位置实体。
 *
 * <p>对应数据库表 {@code QUARKUS_QRY_PROJECTION_POS}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Entity
@Table(name = "QUARKUS_QRY_PROJECTION_POS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuarkusJpaProjectionPosition extends PanacheEntityBase
        implements ProjectionPosition, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "STREAM_ID", nullable = false, length = 250, updatable = false)
    public String streamId;

    @Column(name = "NEXT_EVENT_NUMBER", nullable = false, updatable = true)
    public long nextEventNumber;

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
