package io.ddd4j.sample.quarkus.cqrs.goods.web.dto;

import io.ddd4j.core.util.SFunction;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.quarkus.cqrs.goods.domain.Goods;
import io.ddd4j.sample.quarkus.cqrs.goods.domain.GoodsQuery;
import io.ddd4j.sample.quarkus.cqrs.goods.domain.GoodsStatus;
import jakarta.ws.rs.QueryParam;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

/** HTTP 查询参数到领域查询对象的显式适配器。 */
@Getter
@Setter
public class GoodsQueryParameters {

    @QueryParam("current")
    private Long current;
    @QueryParam("size")
    private Long size;
    @QueryParam("code")
    private String code;
    @QueryParam("nameLike")
    private String nameLike;
    @QueryParam("status")
    private String status;
    @QueryParam("priceMin")
    private BigDecimal priceMin;
    @QueryParam("priceMax")
    private BigDecimal priceMax;
    @QueryParam("orderBys")
    private String orderBys;

    public GoodsQuery toQuery() {
        GoodsQuery query = new GoodsQuery()
                .setCode(code)
                .setNameLike(nameLike)
                .setStatus(parseStatus())
                .setPriceMin(priceMin)
                .setPriceMax(priceMax);
        if (Objects.nonNull(current)) {
            query.setCurrent(current);
        }
        if (Objects.nonNull(size)) {
            query.setSize(size);
        }
        if (StrKit.isNotBlank(orderBys)) {
            applyOrderBys(query);
        }
        return query;
    }

    private void applyOrderBys(GoodsQuery query) {
        for (String orderBy : orderBys.split(",")) {
            int separator = orderBy.lastIndexOf('_');
            String field = separator < 0 ? orderBy : orderBy.substring(0, separator);
            boolean descending = separator >= 0
                    && "DESC".equals(orderBy.substring(separator + 1).toUpperCase(Locale.ROOT));
            switch (field) {
                case "id" -> applyOrder(query, Goods::id, descending);
                case "code" -> applyOrder(query, Goods::getCode, descending);
                case "name" -> applyOrder(query, Goods::getName, descending);
                case "price" -> applyOrder(query, Goods::getPrice, descending);
                case "stock" -> applyOrder(query, Goods::getStock, descending);
                case "status" -> applyOrder(query, Goods::getStatus, descending);
                case "createTime" -> applyOrder(query, Goods::getCreateTime, descending);
                case "updateTime" -> applyOrder(query, Goods::getUpdateTime, descending);
                default -> throw new IllegalArgumentException("unsupported order field: " + field);
            }
        }
    }

    private void applyOrder(GoodsQuery query, SFunction<Goods, ?> property, boolean descending) {
        if (descending) {
            query.orderByDesc(property);
        } else {
            query.orderByAsc(property);
        }
    }

    private GoodsStatus parseStatus() {
        return StrKit.isBlank(status) ? null : GoodsStatus.valueOf(status.toUpperCase(Locale.ROOT));
    }
}
