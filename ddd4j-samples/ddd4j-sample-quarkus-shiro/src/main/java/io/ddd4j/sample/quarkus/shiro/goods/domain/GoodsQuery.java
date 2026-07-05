package io.ddd4j.sample.quarkus.shiro.goods.domain;

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
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class GoodsQuery extends Query<Goods> {

    private static final long serialVersionUID = 1L;

    private String code;
    private String nameLike;
    private GoodsStatus status;
    private BigDecimal priceMin;
    private BigDecimal priceMax;

    @Override
    protected Repository repository() {
        return RepositoryRegistry.repository(Goods.class);
    }
}