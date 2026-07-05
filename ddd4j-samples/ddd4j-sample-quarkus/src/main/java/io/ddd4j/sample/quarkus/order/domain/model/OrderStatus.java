package io.ddd4j.sample.quarkus.order.domain.model;

/**
 * 订单生命周期状态枚举。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public enum OrderStatus {

    /**
     * 草稿
     */
    DRAFT,
    /**
     * 已支付
     */
    PAID,
    /**
     * 已发货
     */
    SHIPPED,
    /**
     * 已取消
     */
    CANCELLED
}