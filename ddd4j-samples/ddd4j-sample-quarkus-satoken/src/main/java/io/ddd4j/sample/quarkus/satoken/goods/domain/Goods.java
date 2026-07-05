package io.ddd4j.sample.quarkus.satoken.goods.domain;

import io.ddd4j.core.ddd.model.AggregateRoot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品 PO 实体（第三轨：Model/Query 快速 CRUD 模式）。
 *
 * <p>仅包含数据字段（@Data），没有复杂的状态机、不维护聚合内集合、不发布领域事件。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Goods extends AggregateRoot<Long> implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String code;
    private String name;
    private BigDecimal price;
    private Integer stock;
    private GoodsStatus status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @Override
    public Long id() {
        return id;
    }
}