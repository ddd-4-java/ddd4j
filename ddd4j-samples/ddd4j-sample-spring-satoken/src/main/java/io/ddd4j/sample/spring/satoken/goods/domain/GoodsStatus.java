package io.ddd4j.sample.spring.satoken.goods.domain;

/**
 * 商品状态枚举。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public enum GoodsStatus {

    /**
     * 上架（在售）。
     */
    ON_SALE,
    /**
     * 下架（停售）。
     */
    OFF_SALE,
    /**
     * 草稿（未上架）。
     */
    DRAFT,
    /**
     * 已删除（软删）。
     */
    DELETED
}
