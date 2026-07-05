package io.ddd4j.sample.javalin.goods.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * 商品标识值对象。
 *
 * <p>值对象：不可变、自描述、相等性基于值（而非引用）。
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

    /**
     * 创建商品 ID 值对象。
     *
     * @param value 主键值
     * @return GoodsId 实例
     */
    public static GoodsId of(Long value) {
        return new GoodsId(value);
    }

    /**
     * 获取主键值。
     *
     * @return 主键
     */
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
