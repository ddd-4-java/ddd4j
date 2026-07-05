package io.ddd4j.sample.spring.security.goods.domain;

import io.ddd4j.core.ddd.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 商品仓储接口（第三轨：Model/Query 快速 CRUD 模式）。
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
