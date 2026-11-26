package io.ddd4j.sample.order.domain.gateway;

import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.sample.order.domain.model.GoodsSkuOffer;
import io.ddd4j.sample.order.domain.model.GoodsSpu;

public interface GoodsSpuRepository extends Repository<GoodsSpu, Long> {
    GoodsSpu findByCode(String code);

    GoodsSkuOffer findOffer(long shopId, String skuCode);
}
