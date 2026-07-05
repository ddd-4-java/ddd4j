package io.ddd4j.sample.javalin.order.domain.model;

/**
 * 订单生命周期状态枚举。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public enum OrderStatus {

    /**
     * 草稿（可继续添加订单行）。
     */
    DRAFT,
    /**
     * 已支付（可发货）。
     */
    PAID,
    /**
     * 已发货。
     */
    SHIPPED,
    /**
     * 已取消。
     */
    CANCELLED
}
