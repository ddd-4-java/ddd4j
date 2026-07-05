package io.ddd4j.sample.spring.goods.domain;

/**
 * 商品状态枚举（第三轨：Model/Query 快速 CRUD 模式）。
 *
 * <p>商品状态相对简单，没有复杂的业务约束，
 * 因此以普通枚举形式定义即可，没有引入状态机。
 *
 * <p>状态流转：
 * <pre>
 *   DRAFT  ──上架──&gt;  ON_SALE
 *   ON_SALE ──下架──&gt;  OFF_SALE
 *   ON_SALE / OFF_SALE / DRAFT ──删除──&gt; DELETED（软删）
 * </pre>
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
