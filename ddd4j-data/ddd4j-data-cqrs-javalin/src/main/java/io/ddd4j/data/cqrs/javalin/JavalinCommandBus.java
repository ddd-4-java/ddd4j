package io.ddd4j.data.cqrs.javalin;

import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.data.cqrs.CommandHandler;
import io.ddd4j.data.cqrs.CommandRegistry;
import io.javalin.Javalin;

import java.util.Collection;
import java.util.Objects;

/**
 * {@link DefaultCommandBus} 的 Javalin 装配适配器：命令契约与路由全部复用
 * ddd4j-core（ADR-0004），本类只做两件事——收集执行器＋组装总线，零新框架
 * 抽象（不 override {@code execute}，不提供 Javalin 插件／事件扩展点）。
 *
 * <p><b>仅服务 Javalin 运行时</b>（Javalin 不内置 DI 容器，无 Bean 装配面）；
 * Spring 系运行时用 {@code ddd4j-data-cqrs-spring}，Quarkus 用
 * {@code ddd4j-data-cqrs-quarkus}，Micronaut 用 {@code ddd4j-data-cqrs-micronaut}，
 * Helidon SE 用 {@code ddd4j-data-cqrs-helidon}，Vert.x 用
 * {@code ddd4j-data-cqrs-vertx}。
 *
 * <h3>装配语义（静态工厂，构造即快照，顺序不可变）</h3>
 * <p>
 * {@link #create} 工厂内先把集成方供入的 {@link CommandExecutor} 集合逐个
 * {@link CommandRegistry#register}（整批拒绝语义），再以
 * {@code super(registry.executors())} 组装总线——{@link DefaultCommandBus}
 * 构造时对集合做一次性快照，后继注册不会回灌，因此收集／注册必须全部发生在
 * {@code super(...)} 之前。<b>刻意不在 {@code app.events(...)} 的 SERVER_STARTED
 * 启动钩子里延迟注册</b>：事件回调在总线构造之后触发，快照语义下迟到的
 * {@code register} 静默无效——正确的 Javalin 接法是在 {@code Javalin.create(...)}
 * 装配线（或任一 handler 挂载之前）手动调 {@code create} 一次性完成注册，
 * {@code app} 参数即装配锚点（强制总线在真实 Javalin 应用实例的存在下创建，
 * 拒绝脱离应用的空中装配）。
 *
 * <p>本实现<b>不拦截注册失败</b>：任一命令类型冲突时 {@code CommandRegistry}
 * 的整批拒绝（all-or-nothing）{@link IllegalStateException} 在装配期立即暴露
 * ——工厂调用失败即整体丢弃，不会留下半注册总线。
 *
 * <h3>{@link CommandHandler @CommandHandler} 发现（ServiceLoader 风格）</h3>
 * <p>
 * 发现机制为 <b>ServiceLoader 风格</b>（Javalin 无容器扫描，JDK SPI 是其
 * 生态的标准装配原语）：执行器实现类以 {@code public} 无参构造注册进集成方
 * 应用的 {@code META-INF/services/io.ddd4j.core.cqrs.command.CommandExecutor}
 * 文件，集成方用 {@code ServiceLoader.load(CommandExecutor.class)} 迭代成
 * 集合供入 {@code create}（工厂吃 {@code Collection} 而非自跑发现——集成方
 * 供入的扫描结果直接接线，发现键与过滤权留在集成方侧）。{@code @CommandHandler}
 * 为声明性元数据，与 {@code supportedCommands()} 的一致性由 SPI 侧约定，
 * 不参与发现（ServiceLoader 的发现键是接口名，非注解）。与
 * {@code SpringCommandBus} 的 {@code getBeansOfType}／{@code HelidonCommandBus}
 * 的 {@code HelidonServiceLoader} 均为「执行器集合的容器原生枚举」等价物，
 * 差异只在发现键。
 *
 * <h3>事务边界刻意不在本层（纯分发路由）</h3>
 * <p>
 * Javalin 没有事务管理栈——本模块<b>零事务依赖</b>：事务由集成方在<b>自己的
 * handler／执行器实现</b>上自管（如经 {@code app.get(...)/app.post(...)} 的
 * handler 内取外部事务管理器包住 {@code execute}，或执行器实现内嵌事务边界），
 * 总线不做 {@code execute} override、不参与事务边界（适配器只做纯分发路由）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see DefaultCommandBus
 * @see CommandRegistry
 * @see CommandHandler
 * @see Javalin
 * @since 2.0.x
 */
public class JavalinCommandBus extends DefaultCommandBus {

    /**
     * 私有构造（静态工厂收口装配）：仅接受已整批注册完成的执行器快照。
     *
     * @param executors 已注册执行器快照（来自 {@code registry.executors()}），非空
     */
    private JavalinCommandBus(Collection<? extends CommandExecutor<?>> executors) {
        super(executors);
    }

    /**
     * 静态装配工厂（集成方一行入口）：把集成方供入的 {@link CommandExecutor}
     * 集合经 {@link CommandRegistry} 整批注册后组装总线。
     * <p>
     * 须在 Javalin 应用装配期（handler 挂载之前）调用一次——
     * {@link DefaultCommandBus} 构造即快照，注册不可延迟到
     * {@code app.events(...)} 启动钩子（见类 javadoc「装配语义」）。
     *
     * @param app       集成方 Javalin 应用实例（装配锚点，确保总线绑定真实应用创建），非空
     * @param executors 集成方经 ServiceLoader／手动扫描供入的执行器候选，非空
     * @return 已完成整批注册的总线
     * @throws IllegalStateException 任一命令类型重复注册（消息含命令类型全限定名）
     */
    public static JavalinCommandBus create(Javalin app, Collection<CommandExecutor<?>> executors) {
        Objects.requireNonNull(app, "app must not be null");
        Objects.requireNonNull(executors, "executors must not be null");
        CommandRegistry registry = new CommandRegistry();
        for (CommandExecutor<?> executor : executors) {
            registry.register(executor);
        }
        return new JavalinCommandBus(registry.executors());
    }
}
