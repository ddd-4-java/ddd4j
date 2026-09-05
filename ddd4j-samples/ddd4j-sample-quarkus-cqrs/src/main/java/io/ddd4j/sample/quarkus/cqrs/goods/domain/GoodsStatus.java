package io.ddd4j.sample.quarkus.cqrs.goods.domain;

/**
 * 商品状态枚举。
 *
 * <p>与 {@code OrderStatus} 不同，本枚举被应用服务直接修改，
 * 不存在任何状态机约束——这是"第三轨"轻量 CRUD 模式的典型特征。
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
