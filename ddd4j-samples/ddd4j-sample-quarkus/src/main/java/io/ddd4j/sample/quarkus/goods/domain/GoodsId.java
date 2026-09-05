package io.ddd4j.sample.quarkus.goods.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * 商品标识值对象（第三轨：Model/Query 快速 CRUD 模式）。
 *
 * <p>值对象：不可变、自描述、相等性基于值（而非引用）。
 * 相比直接使用 {@code Long}，值对象能：
 * <ul>
 *   <li>携带校验逻辑（构造时保证非空且为正）</li>
 *   <li>明确业务语义（区分"商品ID"和"订单ID"等普通 Long）</li>
 *   <li>易于在未来迁移到分布式 ID（雪花算法、UUID）</li>
 * </ul>
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
