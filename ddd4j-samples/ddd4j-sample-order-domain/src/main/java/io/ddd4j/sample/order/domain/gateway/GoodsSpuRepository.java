package io.ddd4j.sample.order.domain.gateway;

import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.sample.order.domain.model.GoodsSkuOffer;
import io.ddd4j.sample.order.domain.model.GoodsSpu;

/**
 * 商品聚合和门店规格报价的持久化端口。
 */
public interface GoodsSpuRepository extends Repository<GoodsSpu, Long> {
    /**
     * 在当前租户查询指定商品。
     *
     * @param code 业务编号
     * @return 商品聚合；不存在时返回 {@code null}
     */
    GoodsSpu findByCode(String code);

    /**
     * 按门店和规格编号查询可用的成交数据。
     *
     * @param shopId  门店主键
     * @param skuCode 租户内 SKU 编号
     * @return 可成交的规格报价；不存在时返回 {@code null}
     */
    GoodsSkuOffer findOffer(long shopId, String skuCode);
}
