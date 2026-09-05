package io.ddd4j.sample.javalin.satoken.goods.web.dto;

import java.util.Objects;
import java.math.BigDecimal;

/**
 * 创建商品 REST 请求。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */public final class CreateGoodsRequest {
        private final String code;
        private final String name;
        private final BigDecimal price;
        private final Integer stock;

        public CreateGoodsRequest(String code, String name, BigDecimal price, Integer stock) {
            this.code = code;
            this.name = name;
            this.price = price;
            this.stock = stock;
        }
        public String code() { return code; }
        public String name() { return name; }
        public BigDecimal price() { return price; }
        public Integer stock() { return stock; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        CreateGoodsRequest other = (CreateGoodsRequest) o;
            return Objects.equals(this.code, other.code) && Objects.equals(this.name, other.name) && Objects.equals(this.price, other.price) && Objects.equals(this.stock, other.stock);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(code, name, price, stock); }
        @Override
        public String toString() {
            return "CreateGoodsRequest{" + "code=" + code + ", " + "name=" + name + ", " + "price=" + price + ", " + "stock=" + stock + "}";
        }
    }