/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.core.cqrs.command;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CommandRegistry} 注册与路由契约测试。
 * 该框架无关契约现由 ddd4j-core 直接承载。
 * <p>
 * 重点守护 brief 修正项（相对计划 sketch 的语义收紧）：register 的
 * <b>整批拒绝</b>（all-or-nothing）语义——多类型执行器任一命令类型冲突时，
 * 全部类型均不落库，不产生半注册状态；与 {@code DefaultCommandBus}
 * 的重复抛 {@link IllegalStateException} 语义一致（ADR-0004）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
class CommandRegistryTest {

    /** \u5f53\u524d\u7528\u4f8b\u6ce8\u518c\u88ab\u6d4b\u7ec4\u4ef6\u548c\u89c2\u5bdf\u6307\u6807\u7684\u5bb9\u5668\u3002 */
    private final CommandRegistry registry = new CommandRegistry();

    /** \u9a8c\u8bc1<code>found</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>executor</code>\u3002 */
    @Test
    void registeredExecutorIsFoundByExactCommandType() {
        CreateOrderCmdExe executor = new CreateOrderCmdExe();

        registry.register(executor);

        CommandExecutor<CreateOrderCommand> found = registry.findExecutor(CreateOrderCommand.class);
        assertSame(executor, found, "findExecutor should return the registered executor instance");
    }

    /** \u9a8c\u8bc1<code>registry.findExecutor(CancelOrderCommand.class)</code>\u4e3a\u7a7a\u3002 */
    @Test
    void findExecutorReturnsNullForUnregisteredCommandType() {
        registry.register(new CreateOrderCmdExe());

        assertNull(registry.findExecutor(CancelOrderCommand.class),
                "unregistered command type should yield null (bus layer owns the missing-executor error)");
    }

    /** \u9a8c\u8bc1<code>registry.register(null)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void registerRejectsNullExecutor() {
        assertThrows(NullPointerException.class, () -> registry.register(null));
    }

    /** \u9a8c\u8bc1<code>registry.register(new CreateOrderCmdExe())</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>ex</code> \u7684\u5f02\u5e38\u6d88\u606f\u5305\u542b <code>CreateOrderCommand.class.getName()</code>\u3002 */
    @Test
    void duplicateCommandTypeRegistrationThrowsIllegalStateWithCommandName() {
        registry.register(new CreateOrderCmdExe());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.register(new CreateOrderCmdExe()));

        assertThat(ex).hasMessageContaining(CreateOrderCommand.class.getName());
    }

    /** \u9a8c\u8bc1<code>registry.register(multiTypeExecutor)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>ex</code> \u7684\u5f02\u5e38\u6d88\u606f\u5305\u542b <code>CreateOrderCommand.class.getName()</code>\uff0c<code>registry.executors()</code> \u7684\u5927\u5c0f\u4e3a <code>1</code>\u3002 */
    @Test
    void conflictingBatchRegistrationIsRejectedEntirely() {
        CreateOrderCmdExe original = new CreateOrderCmdExe();
        registry.register(original);
        OrderLifecycleCmdExe multiTypeExecutor = new OrderLifecycleCmdExe();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.register(multiTypeExecutor));

        assertThat(ex).hasMessageContaining(CreateOrderCommand.class.getName());
        assertSame(original, registry.findExecutor(CreateOrderCommand.class));
        assertNull(registry.findExecutor(CancelOrderCommand.class),
                "non-conflicting types of the rejected executor must NOT be registered (all-or-nothing)");
        assertThat(registry.executors())
                .hasSize(1)
                .doesNotContain(multiTypeExecutor);
    }

    /** \u9a8c\u8bc1<code>registry.executors().add(new CancelOrderCmdExe())</code> \u629b\u51fa <code>UnsupportedOperationException.class</code>\u3002 */
    @Test
    void executorsViewIsImmutable() {
        registry.register(new CreateOrderCmdExe());

        assertThrows(UnsupportedOperationException.class,
                () -> registry.executors().add(new CancelOrderCmdExe()));
    }

    /** \u9a8c\u8bc1<code>registry.findExecutor(CreateOrderCommand.class)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>createExecutor</code>\uff0c<code>registry.findExecutor(CancelOrderCommand.class)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>cancelExecutor</code>\uff0c<code>registry.executors()</code> \u7684\u5927\u5c0f\u4e3a <code>3</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>snapshot</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>original</code>\uff0c<code>registry.executors()</code> \u4ec5\u5305\u542b <code>original</code>\u3001<code>later</code>\u3002 */
    @Test
    void executorsCollectionIsASnapshotOfCompletedRegistrations() {
        CreateOrderCmdExe original = new CreateOrderCmdExe();
        registry.register(original);
        Collection<CommandExecutor<?>> snapshot = registry.executors();
        CancelOrderCmdExe later = new CancelOrderCmdExe();

        registry.register(later);

        assertThat(snapshot).containsExactly(original);
        assertThat(registry.executors()).containsExactlyInAnyOrder(original, later);
    }

    /** \u9a8c\u8bc1<code>registry.register(original)</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>registry.findExecutor(CreateOrderCommand.class)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>original</code>\uff0c<code>registry.executors()</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>original</code>\u3002 */
    @Test
    void registeringTheSameExecutorTwicePreservesTheOriginalRegistration() {
        CreateOrderCmdExe original = new CreateOrderCmdExe();
        registry.register(original);

        assertThrows(IllegalStateException.class, () -> registry.register(original));

        assertSame(original, registry.findExecutor(CreateOrderCommand.class));
        assertThat(registry.executors()).containsExactly(original);
    }

    /** \u9a8c\u8bc1<code>registry.findExecutor(null)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void findExecutorRejectsNullCommandType() {
        assertThrows(NullPointerException.class, () -> registry.findExecutor(null));
    }

    /** \u4f9b\u547d\u4ee4\u603b\u7ebf\u9a8c\u8bc1\u7c7b\u578b\u5339\u914d\u548c\u5206\u53d1\u7684\u547d\u4ee4\u6837\u672c\u3002 */
    static class CreateOrderCommand implements Command {
    }

    /** \u7528\u4e8e\u9a8c\u8bc1\u53d6\u6d88\u8ba2\u5355\u547d\u4ee4\u8def\u7531\u7684\u6837\u672c\u3002 */
    static class CancelOrderCommand implements Command {
    }

    /** \u7528\u4e8e\u9a8c\u8bc1\u8ba2\u5355\u53d1\u8fd0\u547d\u4ee4\u8def\u7531\u7684\u6837\u672c\u3002 */
    static class ShipOrderCommand implements Command {
    }

    /** \u4f9b\u547d\u4ee4\u603b\u7ebf\u9a8c\u8bc1\u7c7b\u578b\u5339\u914d\u548c\u5206\u53d1\u7684\u547d\u4ee4\u6837\u672c\u3002 */
    static class TrackOrderCommand implements Command {
    }

    /** \u63a5\u6536\u6d4b\u8bd5\u547d\u4ee4\u5e76\u8fd4\u56de\u53ef\u65ad\u8a00\u7ed3\u679c\u7684\u6267\u884c\u5668\u66ff\u8eab\u3002 */
    static class CreateOrderCmdExe implements CommandExecutor<CreateOrderCommand> {

        /** {@inheritDoc} */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.<Class<? extends Command>>singleton(CreateOrderCommand.class);
        }

        /** {@inheritDoc} */
        @Override
        public Result execute(CreateOrderCommand command) {
            return Result.ok();
        }
    }

    /** \u63a5\u6536\u6d4b\u8bd5\u547d\u4ee4\u5e76\u8fd4\u56de\u53ef\u65ad\u8a00\u7ed3\u679c\u7684\u6267\u884c\u5668\u66ff\u8eab\u3002 */
    static class CancelOrderCmdExe implements CommandExecutor<CancelOrderCommand> {

        /** {@inheritDoc} */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.<Class<? extends Command>>singleton(CancelOrderCommand.class);
        }

        /** {@inheritDoc} */
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

        /** {@inheritDoc} */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return new LinkedHashSet<>(Arrays.<Class<? extends Command>>asList(
                    CancelOrderCommand.class, CreateOrderCommand.class));
        }

        /** {@inheritDoc} */
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

        /** {@inheritDoc} */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return new HashSet<>(Arrays.<Class<? extends Command>>asList(
                    ShipOrderCommand.class, TrackOrderCommand.class));
        }

        /** {@inheritDoc} */
        @Override
        public Result execute(Command command) {
            return Result.ok();
        }
    }
}
