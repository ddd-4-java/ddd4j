package io.ddd4j.sample.order.infrastructure.persistence.po;

import lombok.Data;
import java.time.LocalDateTime;

/** trade_order_item 持久化对象。 */
@Data
public class TradeOrderItemPO {
    /** 订单明细主键。 */
    private Long id;
    /** 租户标识。 */
    private String tenantId;
    /** 所属本地交易订单主键。 */
    private Long orderId;
    /** 订单内稳定行号，从1开始；重复同步复用原行号。 */
    private Integer lineNo;
    /** 经确认的本地规格主键；目录未匹配时允许为空。 */
    private Long skuId;
    /** 购买时的商品名称，未知为空。 */
    private String productNameSnapshot;
    /** 购买时的规格名称，未知为空。 */
    private String skuNameSnapshot;
    /** 该行购买数量，未知为空。 */
    private Long quantity;
    /** 订单确认的商品单价，人民币分；未知为空。 */
    private Long unitPriceCent;
    /** 创建人ID，0表示系统。 */
    private Long createdBy;
    /** 明细首次写入时间。 */
    private LocalDateTime createdTime;
    /** 最近更新人ID，0表示系统。 */
    private Long updatedBy;
    /** 最近更新时间。 */
    private LocalDateTime updatedTime;
    /** 逻辑删除：0 正常，1 已删除。 */
    private Integer deleted;
}
