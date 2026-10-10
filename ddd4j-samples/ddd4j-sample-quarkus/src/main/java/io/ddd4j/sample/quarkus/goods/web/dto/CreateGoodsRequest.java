package io.ddd4j.sample.quarkus.goods.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

import java.math.BigDecimal;

/**
 * 创建商品请求 DTO。
 *
 * <p>轻量 record：与 Spring MVC 的 record 参数绑定风格一致，
 * 同时被 JAX-RS / Quarkus REST 原生支持。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public final class CreateGoodsRequest {

    private static final long serialVersionUID = 0L;

    private final String code;

    private final String name;

    private final BigDecimal price;

    private final Integer stock;

    @JsonCreator()
    public CreateGoodsRequest(@JsonProperty("code") String code, @JsonProperty("name") String name, @JsonProperty("price") BigDecimal price, @JsonProperty("stock") Integer stock) {
        this.code = code;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    @JsonProperty("code")
    public String code() {
        return code;
    }

    @JsonProperty("name")
    public String name() {
        return name;
    }

    @JsonProperty("price")
    public BigDecimal price() {
        return price;
    }

    @JsonProperty("stock")
    public Integer stock() {
        return stock;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (Objects.isNull(obj) || getClass() != obj.getClass()) {
            return false;
        }
        CreateGoodsRequest other = (CreateGoodsRequest) obj;
        return Objects.equals(this.code, other.code) && Objects.equals(this.name, other.name) && Objects.equals(this.price, other.price) && Objects.equals(this.stock, other.stock);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + Objects.hashCode(code);
        result = 31 * result + Objects.hashCode(name);
        result = 31 * result + Objects.hashCode(price);
        result = 31 * result + Objects.hashCode(stock);
        return result;
    }

    @Override
    public String toString() {
        return "CreateGoodsRequest[code=" + code + ", name=" + name + ", price=" + price + ", stock=" + stock + "]";
    }
}
