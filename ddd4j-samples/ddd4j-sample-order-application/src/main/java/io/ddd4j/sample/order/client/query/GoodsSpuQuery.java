package io.ddd4j.sample.order.client.query;

import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.sample.order.domain.model.GoodsSpu;
import lombok.Value;
import lombok.EqualsAndHashCode;

/** 按租户和商品编码查询商品。 */
@Value
@EqualsAndHashCode(callSuper = false)
public class GoodsSpuQuery extends Query<GoodsSpu> {
    private String tenantId;
    private String spuCode;
}
