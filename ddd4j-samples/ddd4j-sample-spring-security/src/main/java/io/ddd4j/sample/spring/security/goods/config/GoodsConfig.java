package io.ddd4j.sample.spring.security.goods.config;

import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.sample.spring.security.goods.domain.Goods;
import io.ddd4j.sample.spring.security.goods.domain.GoodsQuery;
import io.ddd4j.sample.spring.security.goods.infrastructure.InMemoryGoodsRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the sample Goods repository for Active Record query methods.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class GoodsConfig {

    private final InMemoryGoodsRepository goodsRepository;

    public GoodsConfig(InMemoryGoodsRepository goodsRepository) {
        this.goodsRepository = goodsRepository;
    }

    @PostConstruct
    public void registerGoodsRepository() {
        RepositoryRegistry.register(Goods.class, GoodsQuery.class, goodsRepository);
        log.info("========== Goods repository registered in RepositoryRegistry ==========");
    }
}
