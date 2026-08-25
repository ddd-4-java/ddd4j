package io.ddd4j.data.cqrs.sample;

import io.ddd4j.data.cqrs.CommandRegistry;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

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
 * 注册为共享 Bean。本 IT 引导只带一个关注点（Spring 容器启动）：无数据源／事务
 * 管理器，类级 {@code @Transactional} 刻板保持「已声明、未激活」状态。
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
}
