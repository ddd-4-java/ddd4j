package io.ddd4j.data.cqrs;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link CommandRegistry} 注册与路由契约测试。
 * <p>
 * 重点守护 brief 修正项（相对计划 sketch 的语义收紧）：register 的
 * <b>整批拒绝</b>（all-or-nothing）语义——多类型执行器任一命令类型冲突时，
 * 全部类型均不落库，不产生半注册状态；与 {@code DefaultCommandBus}
 * 的重复抛 {@link IllegalStateException} 语义一致（ADR-0004）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
class CommandRegistryTest {

    private final CommandRegistry registry = new CommandRegistry();

    @Test
    void registeredExecutorIsFoundByExactCommandType() {
        CreateOrderCmdExe executor = new CreateOrderCmdExe();

        registry.register(executor);

        CommandExecutor<CreateOrderCommand> found = registry.findExecutor(CreateOrderCommand.class);
        assertSame(executor, found, "findExecutor should return the registered executor instance");
    }

    @Test
    void findExecutorReturnsNullForUnregisteredCommandType() {
        registry.register(new CreateOrderCmdExe());

        assertNull(registry.findExecutor(CancelOrderCommand.class),
                "unregistered command type should yield null (bus layer owns the missing-executor error)");
    }

    @Test
    void registerRejectsNullExecutor() {
        assertThrows(NullPointerException.class, () -> registry.register(null));
    }

    @Test
    void duplicateCommandTypeRegistrationThrowsIllegalStateWithCommandName() {
        registry.register(new CreateOrderCmdExe());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.register(new CreateOrderCmdExe()));

        assertThat(ex).hasMessageContaining(CreateOrderCommand.class.getName());
    }

    @Test
    void conflictingBatchRegistrationIsRejectedEntirely() {
        registry.register(new CreateOrderCmdExe());
        OrderLifecycleCmdExe multiTypeExecutor = new OrderLifecycleCmdExe();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.register(multiTypeExecutor));

        assertThat(ex).hasMessageContaining(CreateOrderCommand.class.getName());
        assertNull(registry.findExecutor(CancelOrderCommand.class),
                "non-conflicting types of the rejected executor must NOT be registered (all-or-nothing)");
        assertThat(registry.executors())
                .hasSize(1)
                .doesNotContain(multiTypeExecutor);
    }

    @Test
    void executorsViewIsImmutable() {
        registry.register(new CreateOrderCmdExe());

        assertThrows(UnsupportedOperationException.class,
                () -> registry.executors().add(new CancelOrderCmdExe()));
    }

    @Test
    void multipleExecutorsRouteToTheirOwnCommandTypes() {
        CreateOrderCmdExe createExecutor = new CreateOrderCmdExe();
        CancelOrderCmdExe cancelExecutor = new CancelOrderCmdExe();
        OrderFulfillmentCmdExe multiTypeExecutor = new OrderFulfillmentCmdExe();

        registry.register(createExecutor);
        registry.register(cancelExecutor);
        registry.register(multiTypeExecutor);

        assertSame(createExecutor, registry.findExecutor(CreateOrderCommand.class));
        assertSame(cancelExecutor, registry.findExecutor(CancelOrderCommand.class));
        assertSame(multiTypeExecutor, registry.findExecutor(ShipOrderCommand.class));
        assertSame(multiTypeExecutor, registry.findExecutor(TrackOrderCommand.class));
        assertThat(registry.executors())
                .hasSize(3)
                .containsOnlyOnce(multiTypeExecutor);
    }

    static class CreateOrderCommand implements Command {
    }

    static class CancelOrderCommand implements Command {
    }

    static class ShipOrderCommand implements Command {
    }

    static class TrackOrderCommand implements Command {
    }

    static class CreateOrderCmdExe implements CommandExecutor<CreateOrderCommand> {

        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Set.of(CreateOrderCommand.class);
        }

        @Override
        public Result execute(CreateOrderCommand command) {
            return Result.ok();
        }
    }

    static class CancelOrderCmdExe implements CommandExecutor<CancelOrderCommand> {

        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Set.of(CancelOrderCommand.class);
        }

        @Override
        public Result execute(CancelOrderCommand command) {
            return Result.fail("cancelled");
        }
    }

    /**
     * 声明多命令类型的执行器（CreateOrder + CancelOrder），
     * 用于验证整批拒绝语义：与 CreateOrderCmdExe 冲突时 CancelOrder 也不落库。
     */
    static class OrderLifecycleCmdExe implements CommandExecutor<Command> {

        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Set.of(CreateOrderCommand.class, CancelOrderCommand.class);
        }

        @Override
        public Result execute(Command command) {
            return Result.ok();
        }
    }

    /**
     * 声明多命令类型的执行器（ShipOrder + TrackOrder），
     * 用于验证多类型路由与「executors() 中多类型执行器只出现一次」。
     */
    static class OrderFulfillmentCmdExe implements CommandExecutor<Command> {

        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Set.of(ShipOrderCommand.class, TrackOrderCommand.class);
        }

        @Override
        public Result execute(Command command) {
            return Result.ok();
        }
    }
}
