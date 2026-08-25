package io.ddd4j.sample.dropwizard.cqrs.readmodel;

import java.time.LocalDateTime;

/**
 * 订单摘要读模型实体（CQRS 读侧）。
 *
 * <p>由投影视图 {@link OrderSummaryView} 通过事件驱动维护。
 */
public class OrderSummaryViewEntity {

    private String orderId;
    private String orderNo;
    private String buyerId;
    private String buyerName;
    private String status;
    private LocalDateTime createdAt;

    public OrderSummaryViewEntity() {
    }

    public OrderSummaryViewEntity(String orderId, String orderNo, String buyerId, String buyerName, String status) {
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.buyerId = buyerId;
        this.buyerName = buyerName;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getBuyerId() { return buyerId; }
    public void setBuyerId(String buyerId) { this.buyerId = buyerId; }
    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
