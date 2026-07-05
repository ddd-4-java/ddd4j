package io.ddd4j.sample.spring.cqrs.goods.domain;

import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.spring.annotation.DomainEntity;
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
 * <p>本示例刻意保持商品的"轻量 PO"形态：仅包含数据字段（@Data），
 * 没有复杂的状态机、不维护聚合内集合、不发布领域事件。
 * 与同包 {@code order} 子模块的 {@code Order} 充血聚合形成鲜明对比。
 *
 * <p>实体仍继承 {@link AggregateRoot}，因为 ddd4j 的 {@code Repository<M, ID>}
 * 约束仓储只针对聚合根——这是 ddd4j 唯一的领域模型基类。
 * 但本示例的 Goods 不会调用任何充血方法（如 {@code save()} / {@code delete()}），
 * 持久化由应用服务统一编排。
 *
 * <h3>与 {@code Order} 充血模型的对比</h3>
 * <ul>
 *   <li><b>Order</b>：充血聚合根，所有状态变更通过 {@code order.pay()} 等方法，
 *       状态机、不变量、事件全部下沉到聚合根</li>
 *   <li><b>Goods</b>：PO 实体，状态变更由服务直接设置字段，
 *       适合简单业务场景（CRUD 为主，无复杂业务规则）</li>
 * </ul>
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
