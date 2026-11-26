package io.ddd4j.sample.order.domain.model;

import io.ddd4j.core.ddd.model.ValueObject;
import lombok.Value;

/**
 * 门店可购买的目录规格及成交依据。
 */
@Value
public class GoodsSkuOffer implements ValueObject {
    private Long skuId;
    private String spuName;
    private String skuName;
    private Long salePriceCent;
}
