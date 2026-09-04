package io.ddd4j.data.cqrs;

import java.util.Collections;
import io.ddd4j.core.cqrs.command.Command;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 CQRS 命令处理器（写侧发现注解）。
 * <p>
 * 标注于 {@link io.ddd4j.core.cqrs.command.CommandExecutor} 实现类上，
 * 声明本执行器处理的 {@link Command} 类型。各运行时适配器
 * （阶段 6 Task 6.3+：{@code ddd4j-data-cqrs-spring} / {@code -quarkus} / ...）
 * 在装配期扫描此注解，把执行器收集注册到 {@link CommandRegistry}，
 * 再由 {@code new DefaultCommandBus(registry.executors())} 组装命令总线——
 * 命令契约与路由逻辑全部复用 ddd4j-core（ADR-0004：命令分发唯一入口），
 * 本注解只承担「发现」职责，不携带任何行为。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * &#64;CommandHandler(CreateOrderCommand.class)
 * public class CreateOrderCmdExe implements CommandExecutor<CreateOrderCommand> {
 *     &#64;Override
 *     public Set<Class<? extends Command>> supportedCommands() {
 *         return Collections.singleton(CreateOrderCommand.class);
 *     }
 *
 *     &#64;Override
 *     public Result execute(CreateOrderCommand cmd) {
 *         // ...
 *         return Result.ok();
 *     }
 * }
 * }</pre>
 *
 * <p>注解 {@code value} 与 {@code supportedCommands()} 应保持一致；
 * 路由以 {@code supportedCommands()} 为准（与 {@link io.ddd4j.core.cqrs.command.DefaultCommandBus}
 * 的路由键一致），{@code value} 供适配器做声明式校验与文档化。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see CommandRegistry
 * @see io.ddd4j.core.cqrs.command.CommandExecutor
 * @since 2.0.x
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CommandHandler {

    /**
     * 本执行器处理的命令类型。
     *
     * @return 命令类型（须实现 {@link Command}）
     */
    Class<? extends Command> value();
}
