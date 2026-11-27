package io.ddd4j.sample.micronaut.cqrs;

import io.ddd4j.core.cqrs.command.CommandRegistry;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

/** 装配 Micronaut 命令总线使用的注册中心。 */
@Factory
public class CatalogCommandConfiguration {
    /**
     * 按 <code>command Registry</code> 的契约计算并返回结果。
     *
     * @return 当前操作得到的 <code>CommandRegistry</code> 结果
     */
    @Singleton
    CommandRegistry commandRegistry() {
        return new CommandRegistry();
    }
}
