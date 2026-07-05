package io.ddd4j.sample.javalin.shiro.goods.domain;

import io.ddd4j.core.ddd.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 商品仓储接口（第三轨：Model/Query 快速 CRUD 模式）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public interface GoodsRepository extends Repository<Goods, Long> {

    Optional<Goods> findByCode(String code);

    List<Goods> findByStatus(GoodsStatus status);
}