package io.ddd4j.sample.spring.cqrs.goods.domain;

import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 商品充血查询对象（第三轨：Model/Query 快速 CRUD 模式）。
 *
 * <p>继承 ddd4j-core 的 {@link Query} 基类，自动获得充血查询能力：
 * <ul>
 *   <li>{@link #page()} - 分页查询，返回 {@code Page<Goods>}</li>
 *   <li>{@link #list()} - 列表查询，返回 {@code List<Goods>}</li>
 *   <li>{@link #one()} - 单条查询（不存在返回 null）</li>
 *   <li>{@link #oneOpt()} - 单条查询（返回 Optional）</li>
 *   <li>{@link #count()} - 计数</li>
 *   <li>{@link #exist()} / {@link #notExist()} - 存在性判断</li>
 *   <li>{@link #one(String, Object...)} - 断言查询，查不到抛异常</li>
 * </ul>
 *
 * <p>子类只需要绑定到具体的聚合根类型（{@link #repository()}），
 * 即可享受充血查询。所有条件通过 Lombok {@link Data} 自动生成 setter，
 * 业务侧可直接以链式或传统方式构造查询条件。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 1. 链式构建
 * Page<Goods> page = new GoodsQuery()
 *     .setKeyword("iPhone")
 *     .setStatus(GoodsStatus.ON_SALE)
 *     .current(1).size(20)
 *     .orderBy("createTime_DESC")
 *     .page();
 *
 * // 2. 简单计数
 * long count = new GoodsQuery().setStatus(GoodsStatus.ON_SALE).count();
 *
 * // 3. 单条查询
 * Goods goods = new GoodsQuery().setCode("SKU-001").one();
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class GoodsQuery extends Query<Goods> {

    private static final long serialVersionUID = 1L;

    /**
     * 商品编码（精确匹配）。
     */
    private String code;
    /**
     * 商品名称（模糊匹配）。
     */
    private String nameLike;
    /**
     * 商品状态（精确匹配）。
     */
    private GoodsStatus status;
    /**
     * 最低价格（大于等于）。
     */
    private BigDecimal priceMin;
    /**
     * 最高价格（小于等于）。
     */
    private BigDecimal priceMax;

    /**
     * 绑定到 {@link Goods} 聚合根的仓储实例。
     *
     * <p>{@link Query#page()} / {@link #list()} / {@link #one()} 等充血查询方法
     * 会通过本方法获取仓储，再委托给 {@link GoodsRepository}。
     *
     * @return {@link Goods} 聚合根的仓储实例
     */
    @Override
    protected Repository repository() {
        return RepositoryRegistry.repository(Goods.class);
    }
}
