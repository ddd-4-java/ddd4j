package io.ddd4j.sample.spring.satoken.goods.domain;

import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 商品充血查询对象（第三轨：Model/Query 快速 CRUD 模式）。
 *
 * <p>继承 ddd4j-core 的 {@link Query} 基类，自动获得充血查询能力：
 * <ul>
 *   <li>{@link #page()} - 分页查询</li>
 *   <li>{@link #list()} - 列表查询</li>
 *   <li>{@link #one()} / {@link #oneOpt()} - 单条查询</li>
 *   <li>{@link #count()} - 计数</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class GoodsQuery extends Query<Goods> {

    private static final long serialVersionUID = 1L;

    /**
     * 商品编码（精确匹配）。
     */
    private String code;
    /**
     * 商品名称（模糊匹配）。
     */
    private String nameLike;
    /**
     * 商品状态（精确匹配）。
     */
    private GoodsStatus status;
    /**
     * 最低价格（大于等于）。
     */
    private BigDecimal priceMin;
    /**
     * 最高价格（小于等于）。
     */
    private BigDecimal priceMax;

    @Override
    public Repository<Goods, Long> repository() {
        return RepositoryRegistry.repository(Goods.class);
    }
}
