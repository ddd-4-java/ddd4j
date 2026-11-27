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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 共享命令内核的可观察兼容契约；事务边界由平台集成测试负责。
 */
class DefaultCommandBusCompatibilityTest {
    /** \u9a8c\u8bc1<code>type</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>LateCommand.class</code>\uff0c\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>UnsupportedOperationException.class</code>\uff0c<code>executor.executed</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>firstRouted</code>\u3001<code>nextRouted</code>\u3002 */
    @Test
    void missingRouteDiscoveryCachesOnlySuccessfulResolution() {
        /** \u4f9b\u547d\u4ee4\u603b\u7ebf\u9a8c\u8bc1\u7c7b\u578b\u5339\u914d\u548c\u5206\u53d1\u7684\u547d\u4ee4\u6837\u672c\u3002 */
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

    /** \u9a8c\u8bc1<code>actual</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>expected</code>\uff0c<code>actual.getData()</code> \u7b49\u4e8e <code>"order-42"</code>\uff0c<code>executor.executed</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>command</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>actual</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>expected</code>\uff0c<code>actual.isSuccess()</code>\u4e3a\u5047\uff0c<code>executor.executed</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>command</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>bus.execute(command)</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\uff0c<code>executed</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>command</code>\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>failure</code> \u7684\u5f02\u5e38\u6d88\u606f\u5305\u542b <code>CreateOrderCommand.class.getName()</code>\uff0c<code>second.executed</code>\u4e3a\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>bus.execute(new CancelOrderCommand())</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>failure</code> \u7684\u5f02\u5e38\u6d88\u606f\u5305\u542b <code>CancelOrderCommand.class.getName()</code>\uff0c<code>executor.executed</code>\u4e3a\u7a7a\u3002 */
    @Test
    void missingCommandFailsWithoutInvokingAnUnrelatedExecutor() {
        FixtureExecutor executor = new FixtureExecutor(Result.ok(), CreateOrderCommand.class);
        CommandBus bus = new DefaultCommandBus(Collections.singletonList(executor));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> bus.execute(new CancelOrderCommand()));

        assertThat(failure).hasMessageContaining(CancelOrderCommand.class.getName());
        assertThat(executor.executed).isEmpty();
    }

    /** \u9a8c\u8bc1<code>bus.execute(new SpecialCreateOrderCommand())</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>failure</code> \u7684\u5f02\u5e38\u6d88\u606f\u5305\u542b <code>SpecialCreateOrderCommand.class.getName()</code>\uff0c<code>executor.executed</code>\u4e3a\u7a7a\u3002 */
    @Test
    void commandSubclassDoesNotFallBackToItsParentExecutor() {
        FixtureExecutor executor = new FixtureExecutor(Result.ok(), CreateOrderCommand.class);
        CommandBus bus = new DefaultCommandBus(Collections.singletonList(executor));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> bus.execute(new SpecialCreateOrderCommand()));

        assertThat(failure).hasMessageContaining(SpecialCreateOrderCommand.class.getName());
        assertThat(executor.executed).isEmpty();
    }

    /** \u9a8c\u8bc1<code>bus.execute(create)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>lifecycleResult</code>\uff0c<code>bus.execute(ship)</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>shippingResult</code>\uff0c<code>shipping.executed</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>ship</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>bus.&lt;String&gt;execute(command).getData()</code> \u7b49\u4e8e <code>"original"</code>\uff0c<code>bus.execute(new CancelOrderCommand())</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>replacement.executed</code>\u4e3a\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>bus.&lt;String&gt;execute(command).getData()</code> \u7b49\u4e8e <code>"original"</code>\uff0c<code>bus.execute(new CancelOrderCommand())</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>executor.executed</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>command</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>originalBus.execute(new CancelOrderCommand())</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>later.executed</code>\u4e3a\u7a7a\uff0c<code>later.executed</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>command</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>bus.execute(null)</code> \u629b\u51fa <code>NullPointerException.class</code>\uff0c<code>executor.executed</code>\u4e3a\u7a7a\u3002 */
    @Test
    void nullCommandKeepsTheExistingCoreNullPointerContract() {
        FixtureExecutor executor = new FixtureExecutor(Result.ok(), CreateOrderCommand.class);
        CommandBus bus = new DefaultCommandBus(Collections.singletonList(executor));

        assertThrows(NullPointerException.class, () -> bus.execute(null));
        assertThat(executor.executed).isEmpty();
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void nullExecutorCollectionIsRejectedAtConstruction() {
        assertThrows(NullPointerException.class, () -> new DefaultCommandBus(null));
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void nullExecutorIsRejectedAtConstruction() {
        assertThrows(NullPointerException.class,
                () -> new DefaultCommandBus(Collections.singletonList(null)));
    }

    /**
     * 测试命令及其子类用于验证精确类型分发。
     */
    private static class CreateOrderCommand implements Command {
    }

    /** \u7528\u4e8e\u65ad\u8a00\u547d\u4ee4\u7c7b\u578b\u8def\u7531\u7684\u7279\u6b8a\u4e0b\u5355\u547d\u4ee4\u3002 */
    private static final class SpecialCreateOrderCommand extends CreateOrderCommand {
    }

    /** \u7528\u4e8e\u9a8c\u8bc1\u53d6\u6d88\u8ba2\u5355\u547d\u4ee4\u8def\u7531\u7684\u6837\u672c\u3002 */
    private static final class CancelOrderCommand implements Command {
    }

    /** \u7528\u4e8e\u9a8c\u8bc1\u8ba2\u5355\u53d1\u8fd0\u547d\u4ee4\u8def\u7531\u7684\u6837\u672c\u3002 */
    private static final class ShipOrderCommand implements Command {
    }

    /**
     * 可观测执行器记录输入，便于发现误路由、重复执行或错误结果转换。
     */
    private static final class FixtureExecutor implements CommandExecutor<Command> {
        /** \u5939\u5177\u6267\u884c\u5668\u58f0\u660e\u7684\u547d\u4ee4\u7c7b\u578b\u96c6\u5408\u3002 */
        private final Set<Class<? extends Command>> commandTypes;
        /** \u6267\u884c\u88ab\u6d4b\u64cd\u4f5c\u540e\u4fdd\u5b58\u7684\u7ed3\u679c\u3002 */
        private final Result<?> result;
        /** \u6807\u8bb0\u6d4b\u8bd5\u56de\u8c03\u6216\u4e1a\u52a1\u64cd\u4f5c\u5df2\u7ecf\u5b9e\u9645\u6267\u884c\u3002 */
        private final List<Command> executed = new ArrayList<>();

        /**
         * \u6784\u9020\u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684 <code>FixtureExecutor</code> \u5b9e\u4f8b\u3002
         * @param result \u6267\u884c\u88ab\u6d4b\u64cd\u4f5c\u540e\u4fdd\u5b58\u7684\u7ed3\u679c
         * @param commandTypes \u7528\u4e8e\u521d\u59cb\u5316 <code>FixtureExecutor</code> \u7684 <code>Class&lt;? extends Command&gt;[]</code> \u503c
         */
        @SafeVarargs
        private FixtureExecutor(Result<?> result, Class<? extends Command>... commandTypes) {
            this.result = result;
            this.commandTypes = new LinkedHashSet<>(Arrays.asList(commandTypes));
        }

        /**
         * 声明当前处理器可以接收的命令类型。
         *
         * @return 实际返回<code>commandTypes</code>，供调用方取得当前值
         */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return commandTypes;
        }

        /**
         * 用当前处理器执行传入命令或操作。
         *
         * <p>测试夹具会调用 <code>executed.add(command)</code>。</p>
         *
         * @param command 命令对象
         * @return 实际返回<code>result</code>
         */
        @Override
        public Result<?> execute(Command command) {
            executed.add(command);
            return result;
        }
    }
}
