package io.ddd4j.sample.quarkus.cqrs.goods.domain;

import io.ddd4j.core.ddd.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 商品仓储接口（第三轨：Model/Query 快速 CRUD 模式）。
 *
 * <p>继承 ddd4j-core 的 {@link Repository} 接口，标识该仓储操作 {@link Goods} 聚合根。
 * 第三方实现（如 {@code InMemoryGoodsRepository}、MyBatis-Plus 适配等）需提供：
 * <ul>
 *   <li>{@link #findById(Object)} - 按主键查询</li>
 *   <li>{@link #save(Goods)} - 保存或更新</li>
 *   <li>{@link #deleteById(Object)} - 按主键删除（继承自默认方法）</li>
 *   <li>{@link #existsById(Object)} - 判断存在（继承自默认方法）</li>
 * </ul>
 *
 * <p>业务侧可扩展查询方法，例如 {@link #findByCode(String)} / {@link #findByStatus(GoodsStatus)}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface GoodsRepository extends Repository<Goods, Long> {

    /**
     * 根据商品编码查询商品。
     *
     * @param code 商品编码
     * @return 商品（不存在时返回 {@link Optional#empty()}）
     */
    Optional<Goods> findByCode(String code);

    /**
     * 根据状态查询商品列表。
     *
     * @param status 商品状态
     * @return 商品列表
     */
    List<Goods> findByStatus(GoodsStatus status);
}
