package io.ddd4j.sample.quarkus.cqrs.goods.config;

import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.sample.quarkus.cqrs.goods.domain.Goods;
import io.ddd4j.sample.quarkus.cqrs.goods.domain.GoodsQuery;
import io.ddd4j.sample.quarkus.cqrs.goods.infrastructure.InMemoryGoodsRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * 商品模块启动配置（第三轨：Model/Query 快速 CRUD 模式）。
 *
 * <p>Quarkus 启动完成后，将 {@link InMemoryGoodsRepository} 注册到 ddd4j 的
 * {@link RepositoryRegistry}，让 {@link GoodsQuery#page()} / {@code list()} 等
 * 充血查询能正确找到仓储实例。
 *
 * <p>与 {@code GoodsConfig}（Spring 版）不同：本类采用 {@link Observes}
 * {@link StartupEvent} 监听 Quarkus 启动完成事件，而非 Spring 的
 * {@code @PostConstruct}，体现 Quarkus 框架适配细节。
 *
 * <p>注意：生产环境若使用 MyBatis-Plus 适配，可通过 {@code ddd4j-data} 模块的
 * Quarkus 自动配置完成注册，无需手动注册。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@ApplicationScoped
public class GoodsConfig {

    private final InMemoryGoodsRepository productRepository;

    @Inject
    public GoodsConfig(InMemoryGoodsRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Quarkus 启动完成后注册商品仓储到 ddd4j 的全局上下文。
     * <p>
     * 注册后，{@link GoodsQuery#repository()} 调用
     * {@link RepositoryRegistry#repository(Class)} 即可返回本仓储实例。
     *
     * @param event Quarkus 启动事件
     */
    void onStart(@Observes StartupEvent event) {
        RepositoryRegistry.register(Goods.class, GoodsQuery.class, productRepository);
        log.info("[GoodsConfig] GoodsRepository registered: {}", productRepository.getClass().getSimpleName());
    }
}
