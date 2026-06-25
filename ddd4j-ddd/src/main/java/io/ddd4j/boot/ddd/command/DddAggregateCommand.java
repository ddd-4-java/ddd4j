package io.ddd4j.boot.ddd.command;

import org.fuin.ddd4j.core.AggregateRootId;
import org.fuin.ddd4j.core.EntityId;
import org.fuin.cqrs4j.core.AggregateCommand;
import org.fuin.cqrs4j.jackson.AbstractAggregateCommand;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serial;

/**
 * ddd4j 聚合命令基类（纯净 DDD 轨道）。
 *
 * <p>基于 fuinorg {@link AbstractAggregateCommand}，提供 CQRS 写侧的命令契约：
 * <ul>
 *   <li>{@code eventId} — 命令唯一标识（即 commandId，UUID 自动生成）</li>
 *   <li>{@code entityIdPath} — 命令目标聚合的路径</li>
 *   <li>{@code aggregateVersion} — 期望版本（乐观锁）</li>
 *   <li>{@code correlationId} / {@code causationId} — 链路追踪</li>
 *   <li>自动 Jackson 序列化（通过 REST 收发 JSON）</li>
 * </ul>
 *
 * <p>命令是"意图/请求"的载体，由 {@link org.fuin.cqrs4j.core.CommandExecutor} 执行。
 * 命令处理流程：
 * <pre>
 * REST 接收 JSON → 反序列化成 Command → CommandExecutor.execute(ctx, cmd) → 聚合根应用命令 → 产生领域事件
 * </pre>
 *
 * <p>使用方式（参考 ddd-cqrs-4-java-example 的 CreatePersonCommand）：
 * <pre>
 * public class CreateOrderCommand extends DddAggregateCommand&lt;OrderId, OrderId&gt; {
 *     public static final EventType TYPE = new EventType("CreateOrderCommand");
 *
 *     private Money total;
 *
 *     protected CreateOrderCommand() { super(); } // Jackson 反序列化用
 *
 *     public CreateOrderCommand(EntityIdPath path, AggregateVersion version) {
 *         super(path, version);
 *     }
 *
 *     &#64;Override
 *     &#64;JsonIgnore
 *     public EventType getEventType() { return TYPE; }
 *
 *     public static class Builder
 *             extends AbstractAggregateCommand.Builder&lt;OrderId, OrderId, CreateOrderCommand, Builder&gt; {
 *         // ...
 *     }
 * }
 * </pre>
 *
 * @param <ROOT_ID> 聚合根标识类型
 * @param <ENTITY_ID> 命令目标的实体标识类型
 * @author wandl
 * @see AbstractAggregateCommand
 * @see AggregateCommand
 * @since 3.4.x
 */
public abstract class DddAggregateCommand<ROOT_ID extends AggregateRootId, ENTITY_ID extends EntityId>
        extends AbstractAggregateCommand<ROOT_ID, ENTITY_ID> {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 默认构造器（Jackson 反序列化时使用，子类必须保留）。
     */
    protected DddAggregateCommand() {
        super();
    }

}
