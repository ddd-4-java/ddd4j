package io.ddd4j.sample.spring.cqrs.goods.config;

import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.sample.spring.cqrs.goods.domain.Goods;
import io.ddd4j.sample.spring.cqrs.goods.domain.GoodsQuery;
import io.ddd4j.sample.spring.cqrs.goods.infrastructure.InMemoryGoodsRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * 商品模块 Spring 配置（第三轨：Model/Query 快速 CRUD 模式）。
 *
 * <p>启动时将 {@link InMemoryGoodsRepository} 注册到 ddd4j 的 {@link RepositoryRegistry}，
 * 让 {@link GoodsQuery#page()} / {@code list()} 等充血查询能正确找到仓储实例。
 *
 * <p>注意：生产环境若使用 MyBatis-Plus 适配，可通过 {@code ddd4j-data} 模块的 Spring Boot
 * 自动配置（{@code @AutoConfigure}）完成注册，无需手动注册。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class GoodsConfig {

    private final InMemoryGoodsRepository goodsRepository;

    /**
     * 构造函数（由 Spring 注入）。
     *
     * @param goodsRepository 内存商品仓储
     */
    public GoodsConfig(InMemoryGoodsRepository goodsRepository) {
        this.goodsRepository = goodsRepository;
    }

    /**
     * 启动期注册商品仓储到 ddd4j 的全局上下文。
     *
     * <p>注册后，{@link GoodsQuery#repository()} 调用 {@link RepositoryRegistry#repository(Class)}
     * 即可返回本仓储实例。
     */
    @PostConstruct
    public void registerGoodsRepository() {
        RepositoryRegistry.register(Goods.class, GoodsQuery.class, goodsRepository);
        log.info("========== Goods 仓储已注册到 RepositoryRegistry ==========");
    }
}
