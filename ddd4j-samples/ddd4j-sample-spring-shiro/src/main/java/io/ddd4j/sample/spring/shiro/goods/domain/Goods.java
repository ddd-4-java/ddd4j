package io.ddd4j.sample.spring.shiro.goods.domain;

import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.spring.annotation.DomainEntity;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品 PO 实体（第三轨：Model/Query 快速 CRUD 模式）。
 *
 * <p>本示例刻意保持商品的"轻量 PO"形态：仅包含数据字段（@Data），
 * 没有复杂的状态机、不维护聚合内集合、不发布领域事件。
 * 与同包 {@code order} 子模块的 {@code Order} 充血聚合形成鲜明对比。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@DomainEntity(aggregateRoot = true)
public class Goods extends AggregateRoot<Long> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 商品主键 ID。
     */
    private Long id;
    /**
     * 商品编码（业务唯一键）。
     */
    private String code;
    /**
     * 商品名称。
     */
    private String name;
    /**
     * 商品价格。
     */
    private BigDecimal price;
    /**
     * 库存数量。
     */
    private Integer stock;
    /**
     * 商品状态。
     */
    private GoodsStatus status;
    /**
     * 创建时间。
     */
    private LocalDateTime createTime;
    /**
     * 最后更新时间。
     */
    private LocalDateTime updateTime;

    @Override
    public Long id() {
        return id;
    }
}
