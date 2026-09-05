package io.ddd4j.sample.javalin.shiro.goods.web.dto;

import java.util.Objects;
import java.math.BigDecimal;

/**
 * 更新商品 REST 请求。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */public final class UpdateGoodsRequest {
        private final String name;
        private final BigDecimal price;

        public UpdateGoodsRequest(String name, BigDecimal price) {
            this.name = name;
            this.price = price;
        }
        public String name() { return name; }
        public BigDecimal price() { return price; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
        UpdateGoodsRequest other = (UpdateGoodsRequest) o;
            return Objects.equals(this.name, other.name) && Objects.equals(this.price, other.price);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(name, price); }
        @Override
        public String toString() {
            return "UpdateGoodsRequest{" + "name=" + name + ", " + "price=" + price + "}";
        }
    }