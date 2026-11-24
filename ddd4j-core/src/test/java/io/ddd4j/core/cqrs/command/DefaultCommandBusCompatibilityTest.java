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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 共享命令内核的可观察兼容契约；事务边界由平台集成测试负责。
 */
class DefaultCommandBusCompatibilityTest {
    @Test
    void missingRouteDiscoveryCachesOnlySuccessfulResolution() {
        class LateCommand implements Command {
        }
        AtomicInteger lookups = new AtomicInteger();
        IllegalStateException discoveryFailure = new IllegalStateException("discovery-failed");
        Result<String> expected = Result.ok("late");
        FixtureExecutor executor = new FixtureExecutor(expected, LateCommand.class);
        DefaultCommandBus bus = new DefaultCommandBus(Collections.emptyList()) {
            @Override
            protected CommandExecutor<?> resolveMissingExecutor(Class<? extends Command> type,
                                                                Collection<CommandExecutor<?>> registeredExecutors) {
                assertSame(LateCommand.class, type);
                assertThrows(UnsupportedOperationException.class, registeredExecutors::clear);
                if (lookups.incrementAndGet() == 1) {
                    throw discoveryFailure;
                }
                return executor;
            }
        };

        assertSame(discoveryFailure, assertThrows(IllegalStateException.class, () -> bus.execute(new LateCommand())));
        assertThat(executor.executed).isEmpty();
        LateCommand firstRouted = new LateCommand();
        LateCommand nextRouted = new LateCommand();
        assertSame(expected, bus.execute(firstRouted));
        assertSame(expected, bus.execute(nextRouted));
        assertThat(lookups.get()).isEqualTo(2);
        assertThat(executor.executed).containsExactly(firstRouted, nextRouted);
    }

    @Test
    void successResultAndOriginalCommandArePassedThroughExactlyOnce() {
        Result<String> expected = Result.ok("order-42");
        FixtureExecutor executor = new FixtureExecutor(expected, CreateOrderCommand.class);
        CommandBus bus = new DefaultCommandBus(Collections.singletonList(executor));
        CreateOrderCommand command = new CreateOrderCommand();

        Result<String> actual = bus.execute(command);

        assertSame(expected, actual);
        assertThat(actual.getData()).isEqualTo("order-42");
        assertThat(executor.executed).containsExactly(command);
    }

    @Test
    void existingBusinessFailureIsReturnedWithoutThrowingOrRewriting() {
        Result<String> expected = Result.fail(409, "order already cancelled");
        FixtureExecutor executor = new FixtureExecutor(expected, CancelOrderCommand.class);
        CommandBus bus = new DefaultCommandBus(Collections.singletonList(executor));
        CancelOrderCommand command = new CancelOrderCommand();

        Result<String> actual = bus.execute(command);

        assertSame(expected, actual);
        assertThat(actual.isSuccess()).isFalse();
        assertThat(actual.getCode()).isEqualTo(409);
        assertThat(actual.getMessage()).isEqualTo("order already cancelled");
        assertThat(executor.executed).containsExactly(command);
    }

    @Test
    void executorExceptionPropagatesWithoutWrappingOrRetry() {
        IllegalArgumentException expected = new IllegalArgumentException("invalid order state");
        List<Command> executed = new ArrayList<>();
        CommandExecutor<CreateOrderCommand> executor = new CommandExecutor<CreateOrderCommand>() {
            @Override
            public Set<Class<? extends Command>> supportedCommands() {
                return Collections.singleton(CreateOrderCommand.class);
            }

            @Override
            public Result<?> execute(CreateOrderCommand command) {
                executed.add(command);
                throw expected;
            }
        };
        CommandBus bus = new DefaultCommandBus(Collections.singletonList(executor));
        CreateOrderCommand command = new CreateOrderCommand();

        assertSame(expected, assertThrows(IllegalArgumentException.class, () -> bus.execute(command)));
        assertThat(executed).containsExactly(command);
    }

    @Test
    void duplicateCommandRegistrationFailsDuringConstruction() {
        FixtureExecutor first = new FixtureExecutor(Result.ok(), CreateOrderCommand.class);
        FixtureExecutor second = new FixtureExecutor(Result.ok(), CreateOrderCommand.class);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new DefaultCommandBus(Arrays.asList(first, second)));

        assertThat(failure).hasMessageContaining(CreateOrderCommand.class.getName());
        assertThat(first.executed).isEmpty();
        assertThat(second.executed).isEmpty();
    }

    @Test
    void missingCommandFailsWithoutInvokingAnUnrelatedExecutor() {
        FixtureExecutor executor = new FixtureExecutor(Result.ok(), CreateOrderCommand.class);
        CommandBus bus = new DefaultCommandBus(Collections.singletonList(executor));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> bus.execute(new CancelOrderCommand()));

        assertThat(failure).hasMessageContaining(CancelOrderCommand.class.getName());
        assertThat(executor.executed).isEmpty();
    }

    @Test
    void commandSubclassDoesNotFallBackToItsParentExecutor() {
        FixtureExecutor executor = new FixtureExecutor(Result.ok(), CreateOrderCommand.class);
        CommandBus bus = new DefaultCommandBus(Collections.singletonList(executor));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> bus.execute(new SpecialCreateOrderCommand()));

        assertThat(failure).hasMessageContaining(SpecialCreateOrderCommand.class.getName());
        assertThat(executor.executed).isEmpty();
    }

    @Test
    void registryAssemblyRoutesEverySupportedTypeToTheCorrectExecutor() {
        Result<String> lifecycleResult = Result.ok("lifecycle");
        Result<String> shippingResult = Result.ok("shipping");
        FixtureExecutor lifecycle = new FixtureExecutor(lifecycleResult,
                CreateOrderCommand.class, CancelOrderCommand.class);
        FixtureExecutor shipping = new FixtureExecutor(shippingResult, ShipOrderCommand.class);
        CommandRegistry registry = new CommandRegistry();
        registry.register(lifecycle);
        registry.register(shipping);
        CommandBus bus = new DefaultCommandBus(registry.executors());
        CreateOrderCommand create = new CreateOrderCommand();
        CancelOrderCommand cancel = new CancelOrderCommand();
        ShipOrderCommand ship = new ShipOrderCommand();

        assertSame(lifecycleResult, bus.execute(create));
        assertSame(shippingResult, bus.execute(ship));
        assertSame(lifecycleResult, bus.execute(cancel));
        assertThat(lifecycle.executed).containsExactly(create, cancel);
        assertThat(shipping.executed).containsExactly(ship);
    }

    @Test
    void constructionSnapshotsTheSuppliedExecutorCollection() {
        FixtureExecutor original = new FixtureExecutor(Result.ok("original"), CreateOrderCommand.class);
        List<CommandExecutor<?>> executors = new ArrayList<>();
        executors.add(original);
        CommandBus bus = new DefaultCommandBus(executors);
        FixtureExecutor replacement = new FixtureExecutor(Result.ok("replacement"), CreateOrderCommand.class);
        executors.clear();
        executors.add(replacement);
        executors.add(new FixtureExecutor(Result.ok(), CancelOrderCommand.class));
        CreateOrderCommand command = new CreateOrderCommand();

        assertThat(bus.<String>execute(command).getData()).isEqualTo("original");
        assertThrows(IllegalStateException.class, () -> bus.execute(new CancelOrderCommand()));
        assertThat(original.executed).containsExactly(command);
        assertThat(replacement.executed).isEmpty();
    }

    @Test
    void supportedCommandChangesDoNotAlterAnAlreadyConstructedBus() {
        FixtureExecutor executor = new FixtureExecutor(Result.ok("original"), CreateOrderCommand.class);
        CommandBus bus = new DefaultCommandBus(Collections.singletonList(executor));
        executor.commandTypes.clear();
        executor.commandTypes.add(CancelOrderCommand.class);
        CreateOrderCommand command = new CreateOrderCommand();

        assertThat(bus.<String>execute(command).getData()).isEqualTo("original");
        assertThrows(IllegalStateException.class, () -> bus.execute(new CancelOrderCommand()));
        assertThat(executor.executed).containsExactly(command);
    }

    @Test
    void laterRegistryRegistrationOnlyAppearsInANewBus() {
        CommandRegistry registry = new CommandRegistry();
        registry.register(new FixtureExecutor(Result.ok(), CreateOrderCommand.class));
        CommandBus originalBus = new DefaultCommandBus(registry.executors());
        FixtureExecutor later = new FixtureExecutor(Result.ok("later"), CancelOrderCommand.class);
        registry.register(later);

        assertThrows(IllegalStateException.class, () -> originalBus.execute(new CancelOrderCommand()));
        assertThat(later.executed).isEmpty();
        CommandBus rebuiltBus = new DefaultCommandBus(registry.executors());
        CancelOrderCommand command = new CancelOrderCommand();
        assertThat(rebuiltBus.<String>execute(command).getData()).isEqualTo("later");
        assertThat(later.executed).containsExactly(command);
    }

    @Test
    void nullCommandKeepsTheExistingCoreNullPointerContract() {
        FixtureExecutor executor = new FixtureExecutor(Result.ok(), CreateOrderCommand.class);
        CommandBus bus = new DefaultCommandBus(Collections.singletonList(executor));

        assertThrows(NullPointerException.class, () -> bus.execute(null));
        assertThat(executor.executed).isEmpty();
    }

    @Test
    void nullExecutorCollectionIsRejectedAtConstruction() {
        assertThrows(NullPointerException.class, () -> new DefaultCommandBus(null));
    }

    @Test
    void nullExecutorIsRejectedAtConstruction() {
        assertThrows(NullPointerException.class,
                () -> new DefaultCommandBus(Collections.singletonList(null)));
    }

    /** 测试命令及其子类用于验证精确类型分发。 */
    private static class CreateOrderCommand implements Command {
    }

    private static final class SpecialCreateOrderCommand extends CreateOrderCommand {
    }

    private static final class CancelOrderCommand implements Command {
    }

    private static final class ShipOrderCommand implements Command {
    }

    /** 可观测执行器记录输入，便于发现误路由、重复执行或错误结果转换。 */
    private static final class FixtureExecutor implements CommandExecutor<Command> {
        private final Set<Class<? extends Command>> commandTypes;
        private final Result<?> result;
        private final List<Command> executed = new ArrayList<>();

        @SafeVarargs
        private FixtureExecutor(Result<?> result, Class<? extends Command>... commandTypes) {
            this.result = result;
            this.commandTypes = new LinkedHashSet<>(Arrays.asList(commandTypes));
        }

        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return commandTypes;
        }

        @Override
        public Result<?> execute(Command command) {
            executed.add(command);
            return result;
        }
    }
}
