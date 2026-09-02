package io.ddd4j.data.cqrs.sample;

import io.ddd4j.data.cqrs.CommandRegistry;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 集成测试引导应用（Task 6.3，模拟集成方的 {@code @SpringBootApplication}）。
 *
 * <p>刻意不使用 {@code @SpringBootApplication}：本模块不依赖 Boot 运行时，
 * 测试栈以最小装配组合出真实容器——{@code @SpringBootConfiguration}＋自动配置＋
 * {@code @ComponentScan(basePackages = "io.ddd4j.data.cqrs")}（与
 * {@code SpringCommandBus} javadoc 的集成方姿势完全一致：一次扫描同时覆盖
 * {@code io.ddd4j.data.cqrs.spring} 适配器与本 {@code io.ddd4j.data.cqrs.sample}
 * 样例包中的 {@code @Component} 执行器）。
 *
 * <p>{@link CommandRegistry} 是 SPI 纯类（无容器注解，ADR-0005），按集成方姿势
 * 注册为共享 Bean。事务验证（Task 6.3 修复轮）：注册
 * {@link NoopTransactionManager}（真实生命周期、无资源）触发
 * {@code TransactionAutoConfiguration} 激活 {@code @Transactional} 代理——
 * 命令分发经事务代理包裹由 {@code SpringCommandBusIT} 的事务探针用例实证。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = "io.ddd4j.data.cqrs")
public class TestApp {

    /**
     * 注册命令注册中心（SPI 纯类，集成方姿势）。
     *
     * @return CommandRegistry Bean
     */
    @Bean
    CommandRegistry commandRegistry() {
        return new CommandRegistry();
    }

    /**
     * 注册无资源事务管理器（真实 begin/commit/rollback 生命周期）：使容器存在
     * 单一 {@code PlatformTransactionManager} 候选，激活 {@code @Transactional}
     * 代理——分发事务化由此可被探针用例实证（不引 H2/DataSource）。
     *
     * @return PlatformTransactionManager Bean
     */
    @Bean
    PlatformTransactionManager transactionManager() {
        return new NoopTransactionManager();
    }
}
