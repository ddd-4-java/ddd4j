package io.ddd4j.sample.javalin.shiro.order.application;

import java.util.Objects;
import java.math.BigDecimal;

/**
 * 添加订单行命令。
 *
 * @param orderId   订单 ID
 * @param goodsId   商品 ID
 * @param goodsName 商品名称
 * @param quantity  购买数量
 * @param unitPrice 单价
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */public final class AddOrderLineCommand {
        private final String orderId;
        private final String goodsId;
        private final String goodsName;
        private final int quantity;
        private final BigDecimal unitPrice;

        public AddOrderLineCommand(String orderId, String goodsId, String goodsName, int quantity, BigDecimal unitPrice) {
            this.orderId = orderId;
            this.goodsId = goodsId;
            this.goodsName = goodsName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }
        public String orderId() { return orderId; }
        public String goodsId() { return goodsId; }
        public String goodsName() { return goodsName; }
        public int quantity() { return quantity; }
        public BigDecimal unitPrice() { return unitPrice; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        AddOrderLineCommand other = (AddOrderLineCommand) o;
            return Objects.equals(this.orderId, other.orderId) && Objects.equals(this.goodsId, other.goodsId) && Objects.equals(this.goodsName, other.goodsName) && Objects.equals(this.quantity, other.quantity) && Objects.equals(this.unitPrice, other.unitPrice);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(orderId, goodsId, goodsName, quantity, unitPrice); }
        @Override
        public String toString() {
            return "AddOrderLineCommand{" + "orderId=" + orderId + ", " + "goodsId=" + goodsId + ", " + "goodsName=" + goodsName + ", " + "quantity=" + quantity + ", " + "unitPrice=" + unitPrice + "}";
        }
    }