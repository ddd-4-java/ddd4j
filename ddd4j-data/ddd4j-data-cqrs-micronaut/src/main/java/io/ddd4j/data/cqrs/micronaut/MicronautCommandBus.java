package io.ddd4j.data.cqrs.micronaut;

import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.data.cqrs.CommandHandler;
import io.ddd4j.data.cqrs.CommandRegistry;
import io.micronaut.context.BeanContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Objects;

/**
 * {@link DefaultCommandBus} 的 Micronaut 装配适配器：命令契约与路由全部复用
 * ddd4j-core（ADR-0004），本类只做两件事——收集执行器 Bean＋组装总线，
 * 零新框架抽象（不 override {@code execute}，不提供自定义 BeanDefinitionRegistry
 * /BeanCreationEventListener 之类的扩展点）。
 *
 * <p><b>仅服务 Micronaut 运行时</b>；Spring 系运行时用 {@code ddd4j-data-cqrs-spring}
 * 的 {@code SpringCommandBus}，Quarkus 用 {@code ddd4j-data-cqrs-quarkus} 的
 * {@code QuarkusCommandBus}。
 *
 * <h3>装配语义（构造即快照，顺序不可变）</h3>
 * <p>
 * 构造器内先经 {@link BeanContext#getBeansOfType(Class)} 收集容器中全部
 * {@link CommandExecutor} Bean 并逐个 {@link CommandRegistry#register}（整批拒绝
 * 语义），再以 {@code super(registry.executors())} 组装总线——
 * {@link DefaultCommandBus} 构造时对集合做一次性快照，后继注册不会回灌，因此
 * 收集／注册必须全部发生在 {@code super(...)} 之前（不可改为事件监听器之类的
 * 延迟回调注册）。{@code CommandRegistry} 由集成方经 {@code @Factory} 的
 * {@code @Singleton} 工厂方法注册为共享 Bean（SPI 纯类，ADR-0005，刻意不加容器
 * 刻板），{@code BeanContext} 而非 {@code ApplicationContext} 是 Micronaut 4
 * 推荐的注入类型（更窄的接口，只暴露查找语义）。
 *
 * <p>本实现<b>不拦截注册失败</b>：任一命令类型冲突时 {@code CommandRegistry}
 * 的整批拒绝（all-or-nothing）{@link IllegalStateException} 在装配期立即暴露
 * ——Bean 创建失败＝容器启动失败，不会留下半注册总线。
 *
 * <h3>{@link CommandHandler @CommandHandler} 发现</h3>
 * <p>
 * 发现完全依赖 Micronaut 编译期 Bean 定义：执行器类带 {@code @Singleton} 等
 * 刻板（由 {@code micronaut-inject-java} 注解处理器在编译期生成 {@code $Definition}
 * 与 {@code BeanDefinitionReference} 注册文件，运行期 {@code BeanContext} 只读
 * 这些预生成定义——与 Spring 的运行期类路径扫描／CDI 的字节码索引根本不同）。
 * {@code @CommandHandler} 为声明性元数据，与 {@code supportedCommands()} 的一致性
 * 由 SPI 侧约定，不参与发现（真要让注解成为发现键需自定义注解处理器，违反
 * 本阶段「零新框架抽象」约束）。
 *
 * <h3>事务边界刻意不在本层（纯分发路由）</h3>
 * <p>
 * Micronaut 的事务注解是 <b>{@code io.micronaut.transaction.annotation.Transactional}</b>
 * （micronaut-data 提供，并非 jakarta.transaction 同名注解——后者对 Micronaut
 * 运行时不生效）。本模块<b>刻意不引入 micronaut-data / micronaut-data-tx</b>：
 * 事务由集成方标注在<b>自己的 service／执行器 Bean 方法</b>上（AOP 拦截在
 * Bean 层生效），总线不做 {@code execute} override、不参与事务边界——
 * 与 {@code SpringCommandBus}（Spring 侧方法级 {@code @Transactional} 纯委托
 * override）的差异有意图可循：Micronaut 的 {@code @Around} AOP 挂靠在 Bean
 * 方法自身，总线若强行 override 反而引入对 micronaut-data 的依赖面污染。
 *
 * <p>本类<b>不可 {@code final}</b>（Micronaut AOP 代理需子类化）；同理，
 * {@code CommandExecutor} Bean 不得依赖本总线（构造期收集会形成循环依赖）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see DefaultCommandBus
 * @see CommandRegistry
 * @see CommandHandler
 * @since 2.0.x
 */
@Singleton
public class MicronautCommandBus extends DefaultCommandBus {

    /**
     * 收集容器中全部 {@link CommandExecutor} Bean 注册到 {@code registry}，
     * 返回注册中心的执行器快照供 {@link DefaultCommandBus} 构造。
     *
     * @param context  Micronaut Bean 上下文（Micronaut 4 推荐注入类型），非空
     * @param registry 命令注册中心（集成方经 @Factory 注册的共享 Bean），非空
     */
    @Inject
    public MicronautCommandBus(BeanContext context, CommandRegistry registry) {
        super(collect(context, registry));
    }

    private static Collection<CommandExecutor<?>> collect(BeanContext context, CommandRegistry registry) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(registry, "registry must not be null");
        for (CommandExecutor<?> executor : context.getBeansOfType(CommandExecutor.class)) {
            registry.register(executor);
        }
        return registry.executors();
    }
}
