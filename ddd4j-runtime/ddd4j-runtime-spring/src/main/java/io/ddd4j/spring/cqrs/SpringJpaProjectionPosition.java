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

    /** Java \u5e8f\u5217\u5316\u7248\u672c\u6807\u8bc6\u3002 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 事件流 ID
     */
    @Id
    @Column(name = "STREAM_ID", nullable = false, length = 250, updatable = false)
    private String streamId;

    /**
     * 下一条待处理的事件序号
     */
    @Column(name = "NEXT_EVENT_NUMBER", nullable = false, updatable = true)
    private long nextEventNumber;

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
     * \u63a8\u8fdb\u5230\u4e0b\u4e00\u4e2a\u4f4d\u7f6e\uff0c\u8fd4\u56de\u65b0\u7684\u4e0d\u53ef\u53d8\u5b9e\u4f8b\u3002
     *  <p>
     *  \u9075\u5faa {@link ProjectionPosition} \u63a5\u53e3\u5951\u7ea6\uff1a\u4e0d\u4fee\u6539\u5f53\u524d\u5b9e\u4f8b\uff0c\u8fd4\u56de\u5305\u542b\u65b0\u504f\u79fb\u91cf\u7684\u65b0\u5bf9\u8c61\u3002
     *  \u8c03\u7528\u65b9\u901a\u8fc7 {@code repository.save(\u65b0\u5b9e\u4f8b)} \u6301\u4e45\u5316\uff08JPA merge \u8bed\u4e49\uff09\u3002
     * @param nextEventNumber \u65b0\u7684\u4e0b\u4e00\u4e2a\u4e8b\u4ef6\u53f7
     * @return \u5305\u542b\u65b0\u504f\u79fb\u91cf\u7684\u65b0\u5b9e\u4f8b
     */
    @Override
    public ProjectionPosition withNextEventNumber(long nextEventNumber) {
        return new SpringJpaProjectionPosition(this.streamId, nextEventNumber);
    }
}
