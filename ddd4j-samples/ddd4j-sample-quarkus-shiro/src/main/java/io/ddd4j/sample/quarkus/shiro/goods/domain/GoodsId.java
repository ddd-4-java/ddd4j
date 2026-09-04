package io.ddd4j.sample.quarkus.shiro.goods.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * 商品标识值对象。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class GoodsId implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Long value;

    private GoodsId(Long value) {
        if (Objects.isNull(value) || value <= 0) {
            throw new IllegalArgumentException("goodsId must be positive");
        }
        this.value = value;
    }

    public static GoodsId of(Long value) {
        return new GoodsId(value);
    }

    public Long value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GoodsId)) {
            return false;
        }
        GoodsId that = (GoodsId) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "GoodsId{" + value + '}';
    }
}