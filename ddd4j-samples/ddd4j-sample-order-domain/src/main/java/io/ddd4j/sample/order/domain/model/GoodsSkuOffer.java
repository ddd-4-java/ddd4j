package io.ddd4j.sample.order.domain.model;

import io.ddd4j.core.ddd.model.ValueObject;
import lombok.Value;

/**
 * 门店可购买的目录规格及成交依据。
 */
@Value
public class GoodsSkuOffer implements ValueObject {
    /** 目录规格主键。 */
    private Long skuId;
    /** 下单时的商品名称。 */
    private String spuName;
    /** 下单时的规格名称。 */
    private String skuName;
    /** 成交单价，单位为分。 */
    private Long salePriceCent;
}
