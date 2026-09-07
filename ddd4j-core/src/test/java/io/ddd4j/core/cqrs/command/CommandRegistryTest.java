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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link CommandRegistry} 注册与路由契约测试。
 * 该框架无关契约现由 ddd4j-core 直接承载。
 *
 * <p>重点守护 register 的整批拒绝语义：多类型执行器任一命令类型冲突时，
 * 全部类型均不落库，不产生半注册状态。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0.x
 */
class CommandRegistryTest {

    private final CommandRegistry registry = new CommandRegistry();

    @Test
    void registeredExecutorIsFoundByExactCommandType() {
        CreateOrderCmdExe executor = new CreateOrderCmdExe();
        registry.register(executor);
        assertSame(executor, registry.findExecutor(CreateOrderCommand.class));
    }

    @Test
    void findExecutorReturnsNullForUnregisteredCommandType() {
        registry.register(new CreateOrderCmdExe());
        assertNull(registry.findExecutor(CancelOrderCommand.class));
    }

    @Test
    void registerRejectsNullExecutor() {
        assertThrows(NullPointerException.class, () -> registry.register(null));
    }

    @Test
    void duplicateCommandTypeRegistrationThrowsIllegalStateWithCommandName() {
        registry.register(new CreateOrderCmdExe());
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> registry.register(new CreateOrderCmdExe()));
        assertThat(exception).hasMessageContaining(CreateOrderCommand.class.getName());
    }

    @Test
    void conflictingBatchRegistrationIsRejectedEntirely() {
        registry.register(new CreateOrderCmdExe());
        OrderLifecycleCmdExe executor = new OrderLifecycleCmdExe();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> registry.register(executor));

        assertThat(exception).hasMessageContaining(CreateOrderCommand.class.getName());
        assertNull(registry.findExecutor(CancelOrderCommand.class));
        assertThat(registry.executors()).hasSize(1).doesNotContain(executor);
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
        assertThat(registry.executors()).hasSize(3).containsOnlyOnce(multiTypeExecutor);
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
            return Collections.<Class<? extends Command>>singleton(CreateOrderCommand.class);
        }

        @Override
        public Result execute(CreateOrderCommand command) {
            return Result.ok();
        }
    }

    static class CancelOrderCmdExe implements CommandExecutor<CancelOrderCommand> {
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.<Class<? extends Command>>singleton(CancelOrderCommand.class);
        }

        @Override
        public Result execute(CancelOrderCommand command) {
            return Result.fail("cancelled");
        }
    }

    static class OrderLifecycleCmdExe implements CommandExecutor<Command> {
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return new HashSet<>(Arrays.<Class<? extends Command>>asList(
                    CreateOrderCommand.class, CancelOrderCommand.class));
        }

        @Override
        public Result execute(Command command) {
            return Result.ok();
        }
    }

    static class OrderFulfillmentCmdExe implements CommandExecutor<Command> {
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return new HashSet<>(Arrays.<Class<? extends Command>>asList(
                    ShipOrderCommand.class, TrackOrderCommand.class));
        }

        @Override
        public Result execute(Command command) {
            return Result.ok();
        }
    }
}
