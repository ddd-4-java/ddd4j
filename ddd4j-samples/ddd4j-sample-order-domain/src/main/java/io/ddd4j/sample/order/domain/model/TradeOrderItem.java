package io.ddd4j.sample.order.domain.model;

import io.ddd4j.core.ddd.model.Entity;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 包含商品、单价和数量的订单明细实体。
 */
@Value
@Builder
public class TradeOrderItem implements Entity<Long> {
    /**
     * 订单明细主键。
     */
    private Long id;
    /**
     * 租户标识。
     */
    private String tenantId;
    /**
     * 所属本地交易订单主键。
     */
    private Long orderId;
    /**
     * 订单内稳定行号，从1开始；重复同步复用原行号。
     */
    private Integer lineNo;
    /**
     * 经确认的本地规格主键；目录未匹配时允许为空。
     */
    private Long skuId;
    /**
     * 购买时的商品名称，未知为空。
     */
    private String productNameSnapshot;
    /**
     * 购买时的规格名称，未知为空。
     */
    private String skuNameSnapshot;
    /**
     * 该行购买数量，未知为空。
     */
    private Long quantity;
    /**
     * 订单确认的商品单价，人民币分；未知为空。
     */
    private Long unitPriceCent;
    /**
     * 创建人ID，0表示系统。
     */
    private Long createdBy;
    /**
     * 明细首次写入时间。
     */
    private LocalDateTime createdTime;
    /**
     * 最近更新人ID，0表示系统。
     */
    private Long updatedBy;
    /**
     * 最近更新时间。
     */
    private LocalDateTime updatedTime;
    /**
     * 逻辑删除：0 正常，1 已删除。
     */
    private Integer deleted;

    /**
     * 将成交时的商品规格、单价和数量保存为订单明细快照。
     *
     * @param tenant 租户编号
     * @param lineNo 订单内行号
     * @param offer 成交时的目录规格及价格
     * @param quantity 购买数量
     * @return 新的订单明细
     */
    public static TradeOrderItem fromOffer(String tenant, int lineNo, GoodsSkuOffer offer, Long quantity) {
        if (Objects.isNull(quantity) || quantity <= 0) {
            throw new IllegalArgumentException("购买数量必须为正数");
        }
        return builder().tenantId(tenant).lineNo(lineNo).skuId(offer.getSkuId())
                .productNameSnapshot(offer.getSpuName()).skuNameSnapshot(offer.getSkuName())
                .quantity(quantity).unitPriceCent(offer.getSalePriceCent()).build();
    }

    /**
     * 读取<code>id</code>提供给调用方。
     *
     * @return 实际返回<code>id</code>，供调用方取得当前值
     */
    @Override
    public Long id() {
        return id;
    }

    /**
     * \u4f7f\u7528\u6210\u4ea4\u5355\u4ef7\u4e58\u4ee5\u8d2d\u4e70\u4ef6\u6570\u8ba1\u7b97\u660e\u7ec6\u5c0f\u8ba1\u3002
     * @return \u5546\u54c1\u660e\u7ec6\u5c0f\u8ba1\uff0c\u5355\u4f4d\u4e3a\u4eba\u6c11\u5e01\u5206
     * @throws IllegalArgumentException \u8d2d\u4e70\u4ef6\u6570\u975e\u6b63\u6216\u5355\u4ef7\u7f3a\u5931\u3001\u4e3a\u8d1f
     * @throws ArithmeticException \u4e58\u79ef\u8d85\u51fa\u957f\u6574\u6570\u8303\u56f4
     */
    long subtotalCent() {
        if (Objects.isNull(quantity) || quantity <= 0 || Objects.isNull(unitPriceCent) || unitPriceCent < 0) {
            throw new IllegalArgumentException("成交明细须包含正数数量和非负单价");
        }
        return Math.multiplyExact(unitPriceCent, quantity);
    }
}
