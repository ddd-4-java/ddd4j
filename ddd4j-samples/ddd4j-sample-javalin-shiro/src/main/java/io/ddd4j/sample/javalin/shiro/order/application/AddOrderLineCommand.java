package io.ddd4j.sample.javalin.shiro.order.application;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import java.math.BigDecimal;

/**
 * 添加订单行命令。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */

public final class AddOrderLineCommand {

    private static final long serialVersionUID = 0L;

    private final String orderId;

    private final String goodsId;

    private final String goodsName;

    private final int quantity;

    private final BigDecimal unitPrice;

    /**
 * @param orderId 订单 ID
 * @param goodsId 商品 ID
 * @param goodsName 商品名称
 * @param quantity 购买数量
 * @param unitPrice 单价
 */

    @JsonCreator()
    public AddOrderLineCommand(@JsonProperty("orderId") String orderId, @JsonProperty("goodsId") String goodsId, @JsonProperty("goodsName") String goodsName, @JsonProperty("quantity") int quantity, @JsonProperty("unitPrice") BigDecimal unitPrice) {
        this.orderId = orderId;
        this.goodsId = goodsId;
        this.goodsName = goodsName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    @JsonProperty("orderId")
    public String orderId() {
        return orderId;
    }

    @JsonProperty("goodsId")
    public String goodsId() {
        return goodsId;
    }

    @JsonProperty("goodsName")
    public String goodsName() {
        return goodsName;
    }

    @JsonProperty("quantity")
    public int quantity() {
        return quantity;
    }

    @JsonProperty("unitPrice")
    public BigDecimal unitPrice() {
        return unitPrice;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        AddOrderLineCommand other = (AddOrderLineCommand) obj;
        return Objects.equals(this.orderId, other.orderId) && Objects.equals(this.goodsId, other.goodsId) && Objects.equals(this.goodsName, other.goodsName) && this.quantity == other.quantity && Objects.equals(this.unitPrice, other.unitPrice);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(orderId);
        result = 31 * result + Objects.hashCode(goodsId);
        result = 31 * result + Objects.hashCode(goodsName);
        result = 31 * result + Integer.hashCode(quantity);
        result = 31 * result + Objects.hashCode(unitPrice);
        return result;
    }

    @Override
    public String toString() {
        return "AddOrderLineCommand[orderId=" + orderId + ", goodsId=" + goodsId + ", goodsName=" + goodsName + ", quantity=" + quantity + ", unitPrice=" + unitPrice + "]";
    }
}
