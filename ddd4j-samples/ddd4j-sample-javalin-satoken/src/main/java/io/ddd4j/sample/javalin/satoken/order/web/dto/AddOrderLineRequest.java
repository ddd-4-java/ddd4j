package io.ddd4j.sample.javalin.satoken.order.web.dto;

import java.util.Objects;
import java.math.BigDecimal;

/**
 * 添加订单行 REST 请求。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */public final class AddOrderLineRequest {
        private final String goodsId;
        private final String goodsName;
        private final int quantity;
        private final BigDecimal unitPrice;

        public AddOrderLineRequest(String goodsId, String goodsName, int quantity, BigDecimal unitPrice) {
            this.goodsId = goodsId;
            this.goodsName = goodsName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }
        public String goodsId() { return goodsId; }
        public String goodsName() { return goodsName; }
        public int quantity() { return quantity; }
        public BigDecimal unitPrice() { return unitPrice; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        AddOrderLineRequest other = (AddOrderLineRequest) o;
            return Objects.equals(this.goodsId, other.goodsId) && Objects.equals(this.goodsName, other.goodsName) && Objects.equals(this.quantity, other.quantity) && Objects.equals(this.unitPrice, other.unitPrice);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(goodsId, goodsName, quantity, unitPrice); }
        @Override
        public String toString() {
            return "AddOrderLineRequest{" + "goodsId=" + goodsId + ", " + "goodsName=" + goodsName + ", " + "quantity=" + quantity + ", " + "unitPrice=" + unitPrice + "}";
        }
    }