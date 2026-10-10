package io.ddd4j.sample.javalin.shiro.order.application;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

/**
 * 创建订单命令。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */

public final class CreateOrderCommand {

    private static final long serialVersionUID = 0L;

    private final String orderNo;

    private final String buyerId;

    private final String buyerName;

    /**
 * @param orderNo 订单编号
 * @param buyerId 买家 ID
 * @param buyerName 买家显示名称
 */

    @JsonCreator()
    public CreateOrderCommand(@JsonProperty("orderNo") String orderNo, @JsonProperty("buyerId") String buyerId, @JsonProperty("buyerName") String buyerName) {
        this.orderNo = orderNo;
        this.buyerId = buyerId;
        this.buyerName = buyerName;
    }

    @JsonProperty("orderNo")
    public String orderNo() {
        return orderNo;
    }

    @JsonProperty("buyerId")
    public String buyerId() {
        return buyerId;
    }

    @JsonProperty("buyerName")
    public String buyerName() {
        return buyerName;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        CreateOrderCommand other = (CreateOrderCommand) obj;
        return Objects.equals(this.orderNo, other.orderNo) && Objects.equals(this.buyerId, other.buyerId) && Objects.equals(this.buyerName, other.buyerName);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(orderNo);
        result = 31 * result + Objects.hashCode(buyerId);
        result = 31 * result + Objects.hashCode(buyerName);
        return result;
    }

    @Override
    public String toString() {
        return "CreateOrderCommand[orderNo=" + orderNo + ", buyerId=" + buyerId + ", buyerName=" + buyerName + "]";
    }
}
