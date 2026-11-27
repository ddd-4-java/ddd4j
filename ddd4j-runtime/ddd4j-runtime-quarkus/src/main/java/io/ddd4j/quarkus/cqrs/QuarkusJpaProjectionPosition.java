package io.ddd4j.quarkus.cqrs;

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
 * Quarkus 标准 JPA 投影位置实体。
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
public class QuarkusJpaProjectionPosition implements ProjectionPosition, Serializable {

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 事件流 ID
     */
    @Id
    @Column(name = "STREAM_ID", nullable = false, length = 250, updatable = false)
    public String streamId;

    /**
     * 下一条待处理的事件序号
     */
    @Column(name = "NEXT_EVENT_NUMBER", nullable = false, updatable = true)
    public long nextEventNumber;

    /**
     * 返回投影从事件流继续读取时使用的位置。
     *
     * @return 继续读取时使用的事件位置
     */
    @Override
    public long getNextEventNumber() {
        return nextEventNumber;
    }

    /**
     * \u8fd4\u56de\u4e00\u4e2a {@code nextEventNumber} \u5df2\u66f4\u65b0\u7684\u65b0\u5b9e\u4f8b\uff08\u4e0d\u53ef\u53d8\u5951\u7ea6\uff09\u3002
     * @param nextEventNumber \u65b0\u7684\u4e0b\u4e00\u4e2a\u4e8b\u4ef6\u53f7
     * @return \u5305\u542b\u65b0\u4e8b\u4ef6\u53f7\u7684\u65b0\u5b9e\u4f8b\uff0c\u539f\u5b9e\u4f8b\u4e0d\u53d7\u5f71\u54cd
     */
    @Override
    public ProjectionPosition withNextEventNumber(long nextEventNumber) {
        this.nextEventNumber = nextEventNumber;
        return this;
    }
}
