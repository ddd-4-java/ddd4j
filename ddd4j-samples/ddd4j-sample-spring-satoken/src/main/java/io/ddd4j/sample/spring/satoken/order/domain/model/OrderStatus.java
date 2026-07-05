package io.ddd4j.sample.spring.satoken.order.domain.model;

/**
 * 订单状态枚举。
 *
 * <p>状态流转：DRAFT → PAID → SHIPPED / CANCELLED
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public enum OrderStatus {

    /** 草稿（可添加订单行、修改） */
    DRAFT,
    /** 已支付（可发货） */
    PAID,
    /** 已发货（终态） */
    SHIPPED,
    /** 已取消（终态） */
    CANCELLED
}
