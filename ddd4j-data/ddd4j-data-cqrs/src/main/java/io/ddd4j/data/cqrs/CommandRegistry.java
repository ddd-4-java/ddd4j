package io.ddd4j.data.cqrs;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 命令路由注册中心（框架无关）。
 * <p>
 * 由各运行时适配器（阶段 6 Task 6.3+：Spring／Quarkus／...）在装配期扫描
 * {@link CommandHandler @CommandHandler} 注解的 {@link CommandExecutor} 实现类，
 * 逐个注册到本类；随后适配器以 {@code new DefaultCommandBus(registry.executors())}
 * 组装命令总线（ADR-0004：命令分发唯一入口，适配器不复制路由逻辑）。
 * 本类只维护「命令类型 → 执行器」映射，不执行命令、不感知任何运行时框架。
 *
 * <h3>与 DefaultCommandBus 注册语义的差异（有意为之）</h3>
 * <p>
 * {@link io.ddd4j.core.cqrs.command.DefaultCommandBus} 构造时对执行器集合
 * <b>逐个</b> {@code putIfAbsent}，检测到重复才抛 {@link IllegalStateException}——
 * 总线实例是一次性组装对象，构造失败即整体丢弃，半注册状态不会泄漏。
 * 本注册中心则是长生命周期共享组件，若沿用逐个 put 的写法，多类型执行器在
 * 中途冲突时会留下「前几个类型已落库」的半注册状态。因此 {@link #register}
 * 采用 <b>先校验全部类型无冲突、再统一落库</b> 的整批拒绝（all-or-nothing）
 * 语义：任一命令类型已被注册即抛 {@link IllegalStateException}（消息含命令类型名），
 * 且该执行器的所有类型（包括无冲突者）均不落库——比 DefaultCommandBus 的
 * 增量 putIfAbsent 更严谨。两处的重复检测语义（冲突在装配期暴露）均出自
 * ADR-0004 对 fuin 先例的保留决策。
 *
 * <h3>线程模型</h3>
 * <p>
 * 注册发生在装配期（单线程或已外部同步）；内部使用 {@link ConcurrentHashMap}
 * 保证 {@link #findExecutor} 无锁读。并发的重复注册属契约外用法，落库阶段的
 * {@code putIfAbsent} 仅作防御性兜底。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see CommandHandler
 * @see io.ddd4j.core.cqrs.command.DefaultCommandBus
 * @since 2.0.x
 */
public class CommandRegistry {

    private final Map<Class<? extends Command>, CommandExecutor<?>> executors = new ConcurrentHashMap<>();

    /**
     * 注册一个命令执行器（整批拒绝语义）。
     * <p>
     * 先校验 {@link CommandExecutor#supportedCommands()} 声明的全部命令类型
     * 均无冲突，再统一写入；任一类型已被其他执行器注册即抛
     * {@link IllegalStateException}，且本执行器声明的所有类型都不落库。
     *
     * @param executor 命令执行器，非空
     * @throws NullPointerException     executor 为 null
     * @throws IllegalStateException    任一命令类型已被注册（消息含命令类型全限定名）
     */
    public void register(CommandExecutor<?> executor) {
        CommandExecutor<?> actual = Objects.requireNonNull(executor, "executor must not be null");
        Set<Class<? extends Command>> commandTypes = actual.supportedCommands();
        for (Class<? extends Command> commandType : commandTypes) {
            if (executors.containsKey(commandType)) {
                throw new IllegalStateException(
                        "Duplicate command executor registration for command: " + commandType.getName());
            }
        }
        for (Class<? extends Command> commandType : commandTypes) {
            CommandExecutor<?> previous = executors.putIfAbsent(commandType, actual);
            if (Objects.nonNull(previous)) {
                throw new IllegalStateException(
                        "Duplicate command executor registration for command: " + commandType.getName());
            }
        }
    }

    /**
     * 返回已注册执行器的不可变视图（含声明多类型的执行器只出现一次）。
     * <p>
     * 内部映射按「命令类型」键控（多类型执行器对应多个键值），
     * 故以去重集合返回；供适配器组装
     * {@code new DefaultCommandBus(registry.executors())}。
     *
     * @return 已注册执行器的不可变集合
     */
    public Collection<CommandExecutor<?>> executors() {
        return Collections.unmodifiableCollection(new LinkedHashSet<>(executors.values()));
    }

    /**
     * 按命令类型查找执行器。
     *
     * @param commandType 命令类型，非空
     * @param <C>         命令类型泛型
     * @return 对应执行器；未注册返回 {@code null}（未注册命令的报错由
     *         {@link io.ddd4j.core.cqrs.command.CommandBus} 层负责抛出）
     * @throws NullPointerException commandType 为 null
     */
    @SuppressWarnings("unchecked")
    public <C extends Command> CommandExecutor<C> findExecutor(Class<C> commandType) {
        Objects.requireNonNull(commandType, "commandType must not be null");
        return (CommandExecutor<C>) executors.get(commandType);
    }
}
