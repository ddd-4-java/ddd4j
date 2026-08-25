package io.ddd4j.data.cqrs.spring;

import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.data.cqrs.CommandHandler;
import io.ddd4j.data.cqrs.CommandRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Objects;

/**
 * {@link DefaultCommandBus} 的 Spring 装配适配器：命令契约与路由全部复用
 * ddd4j-core（ADR-0004），本类只做两件事——收集执行器 Bean＋组装总线，
 * 零新框架抽象（不 override {@code execute}，不提供自定义 BeanPostProcessor）。
 *
 * <p><b>仅服务 Spring 系运行时</b>（WebMVC／WebFlux／Helidon-Spring）；Quarkus
 * 运行时用 {@code ddd4j-data-cqrs-quarkus} 的 {@code QuarkusCommandBus}。
 *
 * <h3>装配语义（构造即快照，顺序不可变）</h3>
 * <p>
 * 构造器内先经 {@link ApplicationContext#getBeansOfType(Class)} 收集容器中全部
 * {@link CommandExecutor} Bean 并逐个 {@link CommandRegistry#register}（整批拒绝
 * 语义），再以 {@code super(registry.executors())} 组装总线——
 * {@link DefaultCommandBus} 构造时对集合做一次性快照，后继注册不会回灌，因此
 * 收集／注册必须全部发生在 {@code super(...)} 之前（不可改为
 * {@code SmartInitializingSingleton} 之类的延迟回调注册）。
 *
 * <p>本实现<b>不拦截注册失败</b>：任一命令类型冲突时 {@code CommandRegistry}
 * 的整批拒绝（all-or-nothing）{@link IllegalStateException} 在装配期立即暴露
 * ——Bean 创建失败＝容器启动失败，不会留下半注册总线。
 *
 * <h3>{@link CommandHandler @CommandHandler} 发现</h3>
 * <p>
 * 发现完全复用 Spring 内置的 {@code ClassPathScanningCandidateComponentProvider}：
 * 执行器类带 {@code @Component}/{@code @Service} 等刻板（{@code @CommandHandler}
 * 为声明性元数据，与 {@code supportedCommands()} 一致性由 SPI 侧约定），集成方在
 * {@code @SpringBootApplication} 应用类上加一行即可：
 * <pre>{@code
 * &#64;SpringBootApplication
 * &#64;ComponentScan(basePackages = "io.ddd4j.data.cqrs")  // 发现本适配器
 * public class Application {
 *     &#64;Bean  // SPI 纯类（ADR-0005），注册为共享 Bean
 *     CommandRegistry commandRegistry() { return new CommandRegistry(); }
 * }
 * }</pre>
 * 本模块不提供自定义 BeanPostProcessor（ADR-0004：仅扫描＋自动注入）。
 *
 * <h3>事务与代理</h3>
 * <p>
 * 类级 {@link Transactional @Transactional} 包 {@code execute}：容器中存在
 * {@code PlatformTransactionManager} 时由 Spring 代理生效（无事务管理器时刻板静默
 * 不激活）。因此本类<b>不可 {@code final}</b>（CGLIB 代理需子类化）；同理，
 * {@code CommandExecutor} Bean 不得依赖本总线（构造期收集会形成循环依赖）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see DefaultCommandBus
 * @see CommandRegistry
 * @see CommandHandler
 * @since 2.0.x
 */
@Component
@Transactional
public class SpringCommandBus extends DefaultCommandBus {

    /**
     * 收集容器中全部 {@link CommandExecutor} Bean 注册到 {@code registry}，
     * 返回注册中心的执行器快照供 {@link DefaultCommandBus} 构造。
     *
     * @param context  Spring 应用上下文，非空
     * @param registry 命令注册中心（集成方注册的共享 Bean），非空
     */
    @Autowired
    public SpringCommandBus(ApplicationContext context, CommandRegistry registry) {
        super(collect(context, registry));
    }

    private static Collection<CommandExecutor<?>> collect(ApplicationContext context, CommandRegistry registry) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(registry, "registry must not be null");
        for (CommandExecutor<?> executor : context.getBeansOfType(CommandExecutor.class).values()) {
            registry.register(executor);
        }
        return registry.executors();
    }
}
