package io.ddd4j.data.cqrs.quarkus;

import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.DefaultCommandBus;
import io.ddd4j.data.cqrs.CommandHandler;
import io.ddd4j.data.cqrs.CommandRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * {@link DefaultCommandBus} 的 Quarkus（ArC）装配适配器：命令契约与路由全部复用
 * ddd4j-core（ADR-0004），本类只做两件事——收集执行器 Bean＋组装总线，
 * 零新框架抽象（不 override {@code execute}，不提供 Quarkus 扩展／构建步骤）。
 *
 * <p><b>仅服务 Quarkus 运行时</b>；Spring 系运行时（WebMVC/WebFlux/Helidon-Spring）
 * 用 {@code ddd4j-data-cqrs-spring} 的 {@code SpringCommandBus}。
 *
 * <h3>装配语义（构造即快照，顺序不可变）</h3>
 * <p>
 * 构造器内先经 CDI {@link Instance}&lt;{@link CommandExecutor}&gt; 流式收集容器中全部
 * 执行器 Bean 并逐个 {@link CommandRegistry#register}（整批拒绝语义），再以
 * {@code super(registry.executors())} 组装总线——{@link DefaultCommandBus} 构造时
 * 对集合做一次性快照，后继注册不会回灌，因此收集／注册必须全部发生在
 * {@code super(...)} 之前。{@code CommandRegistry} 为构造内局部组装（SPI 纯类，
 * ADR-0005，不注 CDI 刻板）——总线即装配单元，集成方零配置。
 *
 * <p>本实现<b>不拦截注册失败</b>：任一命令类型冲突时 {@code CommandRegistry}
 * 的整批拒绝（all-or-nothing）{@link IllegalStateException} 在装配期立即暴露
 * ——Bean 创建失败，首次解析即抛出。
 *
 * <h3>{@link CommandHandler @CommandHandler} 发现</h3>
 * <p>
 * 发现机制与 {@code SpringCommandBus} 的显式
 * {@code ApplicationContext.getBeansOfType(CommandExecutor.class)} <b>等价</b>：
 * 均为「执行器 Bean 集合」的容器原生枚举。差异在发现键——ArC 按 bean 刻板
 * （{@code @ApplicationScoped} 等）发现执行器实现，{@code @CommandHandler}
 * 在 Quarkus 侧为声明性元数据，不参与发现（真要让注解成为发现键需 Quarkus
 * 扩展构建步骤，违反本阶段「零新框架抽象」约束；若 Task 6.5 Micronaut 适配
 * 引入 {@code BeanContext} 等价枚举，此等价性须重新讨论）。
 *
 * <h3>上下文与代理</h3>
 * <p>
 * 类级 {@link ActivateRequestContext @ActivateRequestContext} 包 {@code execute}：
 * 每次分发激活 CDI Request Context（执行器内的 request-scoped 依赖可解析；Quarkus
 * 的 JTA 事务也要求活动请求上下文）。事务边界不在本层——本模块刻意不引入
 * {@code quarkus-narayana-jta}（与 -event-store-panache 模块的
 * {@code jakarta.transaction.Transactional} 用法区分：那是仓储写事务，这里只保证
 * 分发上下文）。因此本类<b>不可 {@code final}</b>（@ApplicationScoped 客户端代理
 * 需子类化）；同理，执行器 Bean 不得依赖本总线（构造期收集会形成循环依赖）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see DefaultCommandBus
 * @see CommandRegistry
 * @see CommandHandler
 * @since 2.0.x
 */
@ApplicationScoped
@ActivateRequestContext
public class QuarkusCommandBus extends DefaultCommandBus {

    /**
     * 流式收集容器中全部 {@link CommandExecutor} Bean 注册到局部
     * {@code CommandRegistry}，返回注册中心的执行器快照供
     * {@link DefaultCommandBus} 构造。
     *
     * @param executors CDI 执行器枚举（ArC 注入），非空
     */
    @Inject
    public QuarkusCommandBus(Instance<CommandExecutor<?>> executors) {
        super(collect(executors));
    }

    /**
     * 无参构造（<b>仅供 ArC 代理机制使用，业务代码不得调用</b>）：
     * {@code @ApplicationScoped} 正常作用域客户端代理与
     * {@code @ActivateRequestContext} 拦截器在构建期生成的子类需要一个可调用的
     * 非私有无参构造，而 {@link DefaultCommandBus} 只有集合构造（构造即快照，
     * 不能为代理加无参），故在适配器侧补一个以空执行器集初始化的兜底构造——
     * 代理实例的全部业务方法都会被覆盖，空路由表永不参与分发；真实总线一律经
     * {@link #QuarkusCommandBus(Instance)} 装配。
     */
    protected QuarkusCommandBus() {
        super(List.of());
    }

    private static Collection<CommandExecutor<?>> collect(Instance<CommandExecutor<?>> executors) {
        Objects.requireNonNull(executors, "executors must not be null");
        CommandRegistry registry = new CommandRegistry();
        for (CommandExecutor<?> executor : executors) {
            registry.register(executor);
        }
        return registry.executors();
    }
}
