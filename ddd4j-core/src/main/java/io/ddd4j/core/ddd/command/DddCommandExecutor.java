package io.ddd4j.core.ddd.command;

import org.fuin.cqrs4j.core.Command;
import org.fuin.cqrs4j.core.CommandExecutionFailedException;
import org.fuin.cqrs4j.core.CommandExecutor;
import org.fuin.cqrs4j.core.Result;
import org.fuin.ddd4j.core.*;

/**
 * ddd4j 命令执行器基类（纯净 DDD 轨道）。
 *
 * <p>基于 fuinorg {@link CommandExecutor}，提供 CQRS 写侧的统一命令执行入口：
 * <ul>
 *   <li>{@code getCommandTypes()} — 返回本执行器支持的命令类型集合</li>
 *   <li>{@code execute(ctx, cmd)} — 执行命令，返回 {@link Result}</li>
 * </ul>
 *
 * <p>命令处理流程：
 * <pre>
 * REST 接收 JSON → 反序列化成 Command → DddCommandExecutor.execute(ctx, cmd)
 *     → Repository.read(aggregateId)     ← 从事件流重建聚合根
 *     → aggregate.someBusinessMethod()   ← 业务逻辑 → apply(event)
 *     → Repository.update(aggregate)     ← 追加未提交事件到事件流
 *     → return Result.ok(data)
 * </pre>
 *
 * <p>使用方式：
 * <pre>
 * &#64;ApplicationService
 * public class CreateOrderCmdExe extends DddCommandExecutor&lt;CreateOrderCommand&gt; {
 *
 *     private final OrderRepository repository;
 *
 *     &#64;Override
 *     public Set&lt;EventType&gt; getCommandTypes() {
 *         return Set.of(CreateOrderCommand.TYPE);
 *     }
 *
 *     &#64;Override
 *     public Result&lt;Void&gt; execute(Void ctx, CreateOrderCommand cmd)
 *             throws AggregateAlreadyExistsException, CommandExecutionFailedException {
 *         // 1. 验证业务规则
 *         // 2. 创建聚合根（apply 事件）
 *         // 3. repository.add(aggregate)
 *         // 4. return Result.ok(null)
 *     }
 * }
 * </pre>
 *
 * @param <CMD> 命令类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see CommandExecutor
 * @see DddAggregateCommand
 * @since 2.0.x
 */
public abstract class DddCommandExecutor<CMD extends Command>
        implements CommandExecutor<Void, Result<?>, CMD> {

    /**
     * 执行命令。
     *
     * @param ctx 上下文（ddd4j 统一使用 Void，上下文通过 ThreadContext 传递）
     * @param cmd 命令对象
     * @return 执行结果
     * @throws AggregateVersionConflictException 聚合版本冲突
     * @throws AggregateNotFoundException        聚合未找到
     * @throws AggregateVersionNotFoundException 聚合版本未找到
     * @throws AggregateDeletedException         聚合已删除
     * @throws AggregateAlreadyExistsException   聚合已存在
     * @throws CommandExecutionFailedException   其他受检异常
     */
    @Override
    public abstract Result<?> execute(Void ctx, CMD cmd)
            throws AggregateVersionConflictException, AggregateNotFoundException,
            AggregateVersionNotFoundException, AggregateDeletedException,
            AggregateAlreadyExistsException, CommandExecutionFailedException;

}
