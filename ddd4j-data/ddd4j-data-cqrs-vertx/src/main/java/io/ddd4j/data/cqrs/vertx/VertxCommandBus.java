package io.ddd4j.data.cqrs.vertx;

import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.data.cqrs.CommandHandler;
import io.ddd4j.data.cqrs.CommandRegistry;
import io.vertx.core.Vertx;

import java.util.Collection;
import java.util.Objects;

/**
 * {@link DefaultCommandBus} 的 Vert.x 5 装配适配器：命令契约与路由全部复用
 * ddd4j-core（ADR-0004），本类只做两件事——收集执行器＋组装总线，零新框架
 * 抽象（不 override {@code execute}，不暴露 Vert.x {@code Future} 包装，
 * 不提供 Verticle 部署扩展点）。
 *
 * <p><b>仅服务 Vert.x 运行时</b>（Vert.x 无 DI 容器，无 Bean 装配面）；
 * Spring 系运行时用 {@code ddd4j-data-cqrs-spring}，Quarkus 用
 * {@code ddd4j-data-cqrs-quarkus}，Micronaut 用 {@code ddd4j-data-cqrs-micronaut}，
 * Helidon SE 用 {@code ddd4j-data-cqrs-helidon}，Javalin 用
 * {@code ddd4j-data-cqrs-javalin}。
 *
 * <h3>装配语义（静态工厂，构造即快照，顺序不可变）</h3>
 * <p>
 * {@link #create} 工厂内先把集成方供入的 {@link CommandExecutor} 集合逐个
 * {@link CommandRegistry#register}（整批拒绝语义），再以
 * {@code super(registry.executors())} 组装总线——{@link DefaultCommandBus}
 * 构造时对集合做一次性快照，后继注册不会回灌，因此收集／注册必须全部发生在
 * {@code super(...)} 之前。<b>刻意不做延迟装配</b>：无论总线在 Verticle
 * {@code start()} 回调内还是部署前创建，注册都须在 {@code create} 调用内
 * 一次性完成（快照语义下迟到的 {@code register} 静默无效）；{@code vertx}
 * 参数即装配锚点（强制总线在真实 {@link Vertx} 实例的存在下创建，拒绝脱离
 * 运行时的空中装配——实例经 Vert.x 5 的 {@code Vertx.vertx()} 工厂方法获取）。
 *
 * <p>本实现<b>不拦截注册失败</b>：任一命令类型冲突时 {@code CommandRegistry}
 * 的整批拒绝（all-or-nothing）{@link IllegalStateException} 在装配期立即暴露
 * ——工厂调用失败即整体丢弃，不会留下半注册总线。
 *
 * <h3>{@link CommandHandler @CommandHandler} 发现（ServiceLoader 风格）</h3>
 * <p>
 * 发现机制为 <b>ServiceLoader 风格</b>（Vert.x 无容器扫描，JDK SPI 是其
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
 * <h3>同步 execute 刻意不暴露 Vert.x 异步回路</h3>
 * <p>
 * {@code execute} 继承 {@link DefaultCommandBus} 的<b>同步</b>签名（返回
 * {@code Result}，不包 {@code Future}）：总线内部对执行器的调用是同步分发，
 * 不依赖也不占用 Vert.x 事件循环——集成方需要异步语义时把 {@code execute}
 * 委托给 worker 线程（{@code vertx.executeBlocking(...)} 或 Verticle 的
 * worker 部署）自行包装即可，总线不为包装再立抽象（ADR-0004：零新框架抽象）。
 *
 * <h3>事务边界刻意不在本层（纯分发路由）</h3>
 * <p>
 * Vert.x 没有事务管理栈——本模块<b>零事务依赖</b>：事务由集成方在<b>自己的
 * 执行器实现／worker 回调</b>上自管（如经 Vert.x SQL client 的
 * {@code withTransaction} 包住命令落库），总线不做 {@code execute} override、
 * 不参与事务边界（适配器只做纯分发路由）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see DefaultCommandBus
 * @see CommandRegistry
 * @see CommandHandler
 * @see Vertx
 * @since 2.0.x
 */
public class VertxCommandBus extends DefaultCommandBus {

    /**
     * 私有构造（静态工厂收口装配）：仅接受已整批注册完成的执行器快照。
     *
     * @param executors 已注册执行器快照（来自 {@code registry.executors()}），非空
     */
    private VertxCommandBus(Collection<? extends CommandExecutor<?>> executors) {
        super(executors);
    }

    /**
     * 静态装配工厂（集成方一行入口）：把集成方供入的 {@link CommandExecutor}
     * 集合经 {@link CommandRegistry} 整批注册后组装总线。
     * <p>
     * 须在 Vert.x 应用装配期（Verticle 部署之前）调用一次——
     * {@link DefaultCommandBus} 构造即快照，注册不可延迟到部署回调
     * （见类 javadoc「装配语义」）。
     *
     * @param vertx     集成方 Vert.x 实例（装配锚点，确保总线绑定真实运行时创建，
     *                  经 {@code Vertx.vertx()} 获取），非空
     * @param executors 集成方经 ServiceLoader／手动扫描供入的执行器候选，非空
     * @return 已完成整批注册的总线
     * @throws IllegalStateException 任一命令类型重复注册（消息含命令类型全限定名）
     */
    public static VertxCommandBus create(Vertx vertx, Collection<CommandExecutor<?>> executors) {
        Objects.requireNonNull(vertx, "vertx must not be null");
        Objects.requireNonNull(executors, "executors must not be null");
        CommandRegistry registry = new CommandRegistry();
        for (CommandExecutor<?> executor : executors) {
            registry.register(executor);
        }
        return new VertxCommandBus(registry.executors());
    }
}
