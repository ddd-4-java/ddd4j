package io.ddd4j.sample.order.infrastructure.persistence.po;

import lombok.Data;
import java.time.LocalDateTime;

/** trade_order 持久化对象。 */
@Data
public class TradeOrderPO {
    /** 交易订单主键。 */
    private Long id;
    /** 租户标识。 */
    private String tenantId;
    /** 租户内稳定业务订单号，推送及补查共用。 */
    private String orderNo;
    /** 业务交易形态，如团购 GROUP_BUY 或预付 PREPAY；未确认时为空。 */
    private String orderKind;
    /** 约定履约方式，如凭券使用 VOUCHER、到店自提 PICKUP 或预约 RESERVATION；未确认时为空。 */
    private String fulfillmentMode;
    /** 已确认的本地下单门店ID。 */
    private Long shopId;
    /** 已确认的本地买家ID。 */
    private Long buyerId;
    /** 交易生命周期：CREATED 已创建，AWAITING_PAYMENT 待支付，CONFIRMED 已成立，CANCELLED 已取消，CLOSED 已关闭；未知为空。 */
    private String orderState;
    /** 履约结果：PENDING 待履约，PARTIAL 部分完成，FULFILLED 全部完成，EXPIRED 已过期；未知为空。 */
    private String fulfillmentState;
    /** 最近已确认的退款处理状态：NONE 无退款，PROCESSING 处理中，SUCCEEDED 成功，FAILED 失败；未知为空。 */
    private String refundState;
    /** 业务下单时间，UTC；未知为空。 */
    private LocalDateTime placedTime;
    /** 支付成功时间，UTC；未知为空。 */
    private LocalDateTime paidTime;
    /** 整单优惠前金额，人民币分；未知为空。 */
    private Long grossAmountCent;
    /** 商家承担的优惠金额，人民币分；未知为空。 */
    private Long merchantDiscountCent;
    /** 买家实际支付金额，人民币分；未知为空，不扣减后续退款。 */
    private Long paidAmountCent;
    /** 创建人ID，0表示系统。 */
    private Long createdBy;
    /** 订单记录首次写入时间。 */
    private LocalDateTime createdTime;
    /** 最近更新人ID，0表示系统。 */
    private Long updatedBy;
    /** 最近更新时间。 */
    private LocalDateTime updatedTime;
    /** 逻辑删除：0 正常，1 已删除。 */
    private Integer deleted;
}
