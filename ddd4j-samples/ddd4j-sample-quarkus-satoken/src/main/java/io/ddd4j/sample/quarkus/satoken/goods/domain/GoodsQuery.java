package io.ddd4j.sample.quarkus.satoken.goods.domain;

import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.kit.lang.StrKit;
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

    public GoodsQuery setOrderBys(String orderBys) {
        if (io.ddd4j.kit.lang.StrKit.isBlank(orderBys)) {
            return this;
        }
        for (String orderBy : orderBys.split(",")) {
            String[] tokens = orderBy.trim().split("_");
            if (tokens.length != 2) {
                continue;
            }
            boolean desc = "DESC".equalsIgnoreCase(tokens[1]);
            switch (tokens[0]) {
                case "id" -> applyOrder(desc, Goods::id);
                case "createTime" -> applyOrder(desc, Goods::getCreateTime);
                case "updateTime" -> applyOrder(desc, Goods::getUpdateTime);
                case "price" -> applyOrder(desc, Goods::getPrice);
                default -> {
                }
            }
        }
        return this;
    }

    private void applyOrder(boolean desc, io.ddd4j.core.util.SFunction<Goods, ?> property) {
        if (desc) {
            orderByDesc(property);
            return;
        }
        orderByAsc(property);
    }

    @Override
    public Repository<Goods, Long> repository() {
        return RepositoryRegistry.repository(Goods.class);
    }
}