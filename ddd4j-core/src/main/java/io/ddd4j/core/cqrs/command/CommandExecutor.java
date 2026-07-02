package io.ddd4j.core.cqrs.command;

import java.util.Set;

/**
 * CQRS 命令执行器接口（写侧入口）。
 * <p>
 * 取代 {@code io.ddd4j.core.ddd.command.DddCommandExecutor}，移除对 fuinorg 的依赖。
 *
 * <h3>执行器契约</h3>
 * <ul>
 *   <li><b>{@code supportedCommands}</b>：返回本执行器支持的命令类型集合，用于命令路由</li>
 *   <li><b>{@code execute}</b>：执行命令，返回 {@link Result}</li>
 *   <li><b>线程模型</b>：执行器本身无状态，多实例可并行执行</li>
 * </ul>
 *
 * <h3>注册与路由</h3>
 * <p>
 * 业务方实现 {@code CommandExecutor}，由框架适配层扫描并注册到
 * {@code io.ddd4j.core.cqrs.command.CommandRegistry}：
 * </p>
 *
 * <pre>{@code
 * // 业务侧实现
 * &#64;ApplicationService
 * public class CreateOrderCmdExe implements CommandExecutor<CreateOrderCommand> {
 *     &#64;Override
 *     public Set<Class<? extends Command>> supportedCommands() {
 *         return Set.of(CreateOrderCommand.class);
 *     }
 *
 *     &#64;Override
 *     public Result execute(CreateOrderCommand cmd) {
 *         Order order = new Order(cmd.getOrderId(), cmd.getTotal());
 *         orderRepository.save(order);
 *         return Result.ok();
 *     }
 * }
 *
 * // 框架适配层（Spring/Quarkus/Guice）自动扫描 @ApplicationService
 * // 并注册到 CommandRegistry，按命令类型路由
 * }</pre>
 *
 * @param <C> 命令类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.0
 */
public interface CommandExecutor<C extends Command> {

    /**
     * 返回本执行器支持的命令类型集合。
     *
     * @return 支持的命令类型集合
     */
    Set<Class<? extends Command>> supportedCommands();

    /**
     * 执行命令。
     *
     * @param command 命令对象
     * @return 执行结果
     */
    Result execute(C command);
}