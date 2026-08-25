package io.ddd4j.data.cqrs.micronaut;

import io.ddd4j.data.cqrs.CommandRegistry;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

/**
 * 集成方装配姿势的最小样例（集成测试专用）：{@code CommandRegistry} 是 SPI 纯类
 * （ADR-0005，刻意不带容器刻板），集成方经 {@code @Factory} 工厂方法把它注册为
 * 共享 Bean——与 {@code SpringCommandBus} 侧 {@code TestApp} 的 {@code @Bean}
 * 注册姿势等价，仅发现机制换成 Micronaut 编译期 Bean 定义。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Factory
class TestFactory {

    /**
     * 注册中心作为共享单例 Bean 供 {@code MicronautCommandBus} 构造注入。
     *
     * @return 新的命令注册中心（装配期专用，无状态）
     */
    @Singleton
    CommandRegistry commandRegistry() {
        return new CommandRegistry();
    }
}
