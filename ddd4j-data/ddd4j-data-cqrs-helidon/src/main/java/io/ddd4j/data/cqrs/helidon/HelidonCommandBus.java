package io.ddd4j.data.cqrs.helidon;

import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.data.cqrs.CommandHandler;
import io.ddd4j.data.cqrs.CommandRegistry;
import io.helidon.common.serviceloader.HelidonServiceLoader;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * {@link DefaultCommandBus} 的 Helidon SE 装配适配器：命令契约与路由全部复用
 * ddd4j-core（ADR-0004），本类只做两件事——收集执行器服务＋组装总线，
 * 零新框架抽象（不 override {@code execute}，不提供自定义 Helidon Features/
 * Service 注册扩展点）。
 *
 * <p><b>仅服务 Helidon SE 运行时</b>（不开 MicroProfile CDI——MP 线的
 * {@code helidon-microprofile-cdi} 属另一装配面）；Spring 系运行时用
 * {@code ddd4j-data-cqrs-spring}，Quarkus 用 {@code ddd4j-data-cqrs-quarkus}，
 * Micronaut 用 {@code ddd4j-data-cqrs-micronaut}。
 *
 * <h3>装配语义（构造即快照，顺序不可变）</h3>
 * <p>
 * 构造器内先经 {@link HelidonServiceLoader}（Helidon SE 对 JDK
 * {@link java.util.ServiceLoader} 的官方包装：优先级排序＋系统属性排除）迭代
 * {@code META-INF/services} 声明的全部 {@link CommandExecutor} 服务并逐个
 * {@link CommandRegistry#register}（整批拒绝语义），再以
 * {@code super(registry.executors())} 组装总线——{@link DefaultCommandBus}
 * 构造时对集合做一次性快照，后继注册不会回灌，因此收集／注册必须全部发生在
 * {@code super(...)} 之前（不可改为延迟回调注册）。{@code CommandRegistry}
 * 为构造内传入的装配单元（SPI 纯类，ADR-0005，不注册为服务）。
 *
 * <p>本实现<b>不拦截注册失败</b>：任一命令类型冲突时 {@code CommandRegistry}
 * 的整批拒绝（all-or-nothing）{@link IllegalStateException} 在装配期立即暴露
 * ——构造失败即整体丢弃，不会留下半注册总线。
 *
 * <h3>{@link CommandHandler @CommandHandler} 发现</h3>
 * <p>
 * 发现机制为 <b>ServiceLoader 风格</b>（Helidon SE 的服务装配原语）：执行器实现类
 * 以 {@code public} 无参构造注册进集成方应用的
 * {@code META-INF/services/io.ddd4j.core.cqrs.command.CommandExecutor} 文件，
 * {@link HelidonServiceLoader} 迭代装配。{@code @CommandHandler} 为声明性元数据，
 * 与 {@code supportedCommands()} 的一致性由 SPI 侧约定，不参与发现
 * （ServiceLoader 的发现键是接口名，非注解）。与 {@code SpringCommandBus} 的
 * {@code getBeansOfType}／{@code MicronautCommandBus} 的编译期 Bean 定义
 * 均为「执行器集合的容器原生枚举」等价物，差异只在发现键。
 *
 * <h3>事务边界刻意不在本层（纯分发路由）</h3>
 * <p>
 * Helidon 的事务栈是 {@code helidon-data-tx-jpa}（Helidon 4 线）等持久化集成，
 * 与本模块完全解耦——本模块<b>零事务依赖</b>：事务由集成方标注在<b>自己的
 * service／执行器实现</b>上（或外层事务边界内调用总线），总线不做
 * {@code execute} override、不参与事务边界（适配器只做纯分发路由）。
 *
 * <p>构造器上的 {@link Inject @Inject} 与类上的 {@link Singleton @Singleton}
 * （jakarta.inject，Helidon SE 核心即用 jakarta.inject 而非 CDI）为声明性刻板：
 * 无容器运行时集成方可直接 {@code new}，jakarta.inject 运行时（如未来的
 * Helidon DI 线）可按构造器注入装配。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see DefaultCommandBus
 * @see CommandRegistry
 * @see CommandHandler
 * @see HelidonServiceLoader
 * @since 2.0.x
 */
@Singleton
public class HelidonCommandBus extends DefaultCommandBus {

    /**
     * 收集 {@link HelidonServiceLoader} 枚举的全部 {@link CommandExecutor} 服务
     * 注册到 {@code registry}，返回注册中心的执行器快照供
     * {@link DefaultCommandBus} 构造。
     *
     * @param executors Helidon 服务加载器（{@code HelidonServiceLoader.create(
     *                  ServiceLoader.load(CommandExecutor.class))} 的自然产物），
     *                  通配形参同时兼容带具体泛型的服务加载器，非空
     * @param registry  命令注册中心（装配期专用），非空
     */
    @Inject
    public HelidonCommandBus(HelidonServiceLoader<? extends CommandExecutor<?>> executors,
            CommandRegistry registry) {
        super(collect(executors, registry));
    }

    /**
     * 便捷装配（集成方一行入口）：以当前 classpath 的
     * {@code META-INF/services/io.ddd4j.core.cqrs.command.CommandExecutor} 声明
     * 构造 {@link HelidonServiceLoader} 装配总线。
     * <p>
     * {@code ServiceLoader.load(CommandExecutor.class)} 的静态类型是
     * {@code ServiceLoader<CommandExecutor>}（raw 形参），与构造器的
     * {@code HelidonServiceLoader<? extends CommandExecutor<?>>} 通配形参不可直赋，
     * 本工厂把这一次 raw 泛型擦除转换集中收口（{@code @SuppressWarnings} 单点），
     * 集成方与测试免于各自 unchecked cast。
     *
     * @param registry 命令注册中心（装配期专用），非空
     * @return 经真实 ServiceLoader 发现装配的总线
     */
    public static HelidonCommandBus discover(CommandRegistry registry) {
        Objects.requireNonNull(registry, "registry must not be null");
        @SuppressWarnings({"unchecked", "rawtypes"})
        ServiceLoader<CommandExecutor<?>> services =
                (ServiceLoader) ServiceLoader.load(CommandExecutor.class);
        return new HelidonCommandBus(HelidonServiceLoader.create(services), registry);
    }

    private static Collection<CommandExecutor<?>> collect(
            HelidonServiceLoader<? extends CommandExecutor<?>> executors, CommandRegistry registry) {
        Objects.requireNonNull(executors, "executors must not be null");
        Objects.requireNonNull(registry, "registry must not be null");
        for (CommandExecutor<?> executor : executors) {
            registry.register(executor);
        }
        return registry.executors();
    }
}
