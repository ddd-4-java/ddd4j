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

import io.ddd4j.core.api.R;

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
    /**
     * 验证<code>type</code> 与…为同一实例 <code>LateCommand.class</code>，被测调用 抛出 <code>UnsupportedOperationException.class</code>，<code>executor.executed</code> 按顺序仅包含 <code>firstRouted</code>、<code>nextRouted</code>。
     */
    @Test
    void missingRouteDiscoveryCachesOnlySuccessfulResolution() {
        /** 供命令总线验证类型匹配和分发的命令样本。 */
        class LateCommand implements Command {
        }
        AtomicInteger lookups = new AtomicInteger();
        IllegalStateException discoveryFailure = new IllegalStateException("discovery-failed");
        R<String> expected = R.ok("late");
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

    /**
     * 验证<code>actual</code> 与…为同一实例 <code>expected</code>，<code>actual.getData()</code> 等于 <code>"order-42"</code>，<code>executor.executed</code> 按顺序仅包含 <code>command</code>。
     */
    @Test
    void successResultAndOriginalCommandArePassedThroughExactlyOnce() {
        R<String> expected = R.ok("order-42");
        FixtureExecutor executor = new FixtureExecutor(expected, CreateOrderCommand.class);
        CommandBus bus = new DefaultCommandBus(Collections.singletonList(executor));
        CreateOrderCommand command = new CreateOrderCommand();

        R<String> actual = bus.execute(command);

        assertSame(expected, actual);
        assertThat(actual.isOk()).isTrue();
        assertThat(actual.getCode()).isEqualTo(0);
        assertThat(actual.getData()).isEqualTo("order-42");
        assertThat(executor.executed).containsExactly(command);
    }

    /**
     * 验证<code>actual</code> 与…为同一实例 <code>expected</code>，<code>actual.isOk()</code>为假，<code>executor.executed</code> 按顺序仅包含 <code>command</code>。
     */
    @Test
    void existingBusinessFailureIsReturnedWithoutThrowingOrRewriting() {
        R<String> expected = R.of(409, "order already cancelled");
        FixtureExecutor executor = new FixtureExecutor(expected, CancelOrderCommand.class);
        CommandBus bus = new DefaultCommandBus(Collections.singletonList(executor));
        CancelOrderCommand command = new CancelOrderCommand();

        R<String> actual = bus.execute(command);

        assertSame(expected, actual);
        assertThat(actual.isOk()).isFalse();
        assertThat(actual.getCode()).isEqualTo(409);
        assertThat(actual.getMsg()).isEqualTo("order already cancelled");
        assertThat(executor.executed).containsExactly(command);
    }

    /**
     * 验证<code>bus.execute(command)</code> 抛出 <code>IllegalArgumentException.class</code>，<code>executed</code> 按顺序仅包含 <code>command</code>。
     */
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
            public R<?> execute(CreateOrderCommand command) {
                executed.add(command);
                throw expected;
            }
        };
        CommandBus bus = new DefaultCommandBus(Collections.singletonList(executor));
        CreateOrderCommand command = new CreateOrderCommand();

        assertSame(expected, assertThrows(IllegalArgumentException.class, () -> bus.execute(command)));
        assertThat(executed).containsExactly(command);
    }

    /**
     * 验证被测调用 抛出 <code>IllegalStateException.class</code>，<code>failure</code> 的异常消息包含 <code>CreateOrderCommand.class.getName()</code>，<code>second.executed</code>为空。
     */
    @Test
    void duplicateCommandRegistrationFailsDuringConstruction() {
        FixtureExecutor first = new FixtureExecutor(R.ok(), CreateOrderCommand.class);
        FixtureExecutor second = new FixtureExecutor(R.ok(), CreateOrderCommand.class);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new DefaultCommandBus(Arrays.asList(first, second)));

        assertThat(failure).hasMessageContaining(CreateOrderCommand.class.getName());
        assertThat(first.executed).isEmpty();
        assertThat(second.executed).isEmpty();
    }

    /**
     * 验证<code>bus.execute(new CancelOrderCommand())</code> 抛出 <code>IllegalStateException.class</code>，<code>failure</code> 的异常消息包含 <code>CancelOrderCommand.class.getName()</code>，<code>executor.executed</code>为空。
     */
    @Test
    void missingCommandFailsWithoutInvokingAnUnrelatedExecutor() {
        FixtureExecutor executor = new FixtureExecutor(R.ok(), CreateOrderCommand.class);
        CommandBus bus = new DefaultCommandBus(Collections.singletonList(executor));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> bus.execute(new CancelOrderCommand()));

        assertThat(failure).hasMessageContaining(CancelOrderCommand.class.getName());
        assertThat(executor.executed).isEmpty();
    }

    /**
     * 验证<code>bus.execute(new SpecialCreateOrderCommand())</code> 抛出 <code>IllegalStateException.class</code>，<code>failure</code> 的异常消息包含 <code>SpecialCreateOrderCommand.class.getName()</code>，<code>executor.executed</code>为空。
     */
    @Test
    void commandSubclassDoesNotFallBackToItsParentExecutor() {
        FixtureExecutor executor = new FixtureExecutor(R.ok(), CreateOrderCommand.class);
        CommandBus bus = new DefaultCommandBus(Collections.singletonList(executor));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> bus.execute(new SpecialCreateOrderCommand()));

        assertThat(failure).hasMessageContaining(SpecialCreateOrderCommand.class.getName());
        assertThat(executor.executed).isEmpty();
    }

    /**
     * 验证<code>bus.execute(create)</code> 与…为同一实例 <code>lifecycleResult</code>，<code>bus.execute(ship)</code> 与…为同一实例 <code>shippingResult</code>，<code>shipping.executed</code> 按顺序仅包含 <code>ship</code>。
     */
    @Test
    void registryAssemblyRoutesEverySupportedTypeToTheCorrectExecutor() {
        R<String> lifecycleResult = R.ok("lifecycle");
        R<String> shippingResult = R.ok("shipping");
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

    /**
     * 验证<code>bus.&lt;String&gt;execute(command).getData()</code> 等于 <code>"original"</code>，<code>bus.execute(new CancelOrderCommand())</code> 抛出 <code>IllegalStateException.class</code>，<code>replacement.executed</code>为空。
     */
    @Test
    void constructionSnapshotsTheSuppliedExecutorCollection() {
        FixtureExecutor original = new FixtureExecutor(R.ok("original"), CreateOrderCommand.class);
        List<CommandExecutor<?>> executors = new ArrayList<>();
        executors.add(original);
        CommandBus bus = new DefaultCommandBus(executors);
        FixtureExecutor replacement = new FixtureExecutor(R.ok("replacement"), CreateOrderCommand.class);
        executors.clear();
        executors.add(replacement);
        executors.add(new FixtureExecutor(R.ok(), CancelOrderCommand.class));
        CreateOrderCommand command = new CreateOrderCommand();

        assertThat(bus.<String>execute(command).getData()).isEqualTo("original");
        assertThrows(IllegalStateException.class, () -> bus.execute(new CancelOrderCommand()));
        assertThat(original.executed).containsExactly(command);
        assertThat(replacement.executed).isEmpty();
    }

    /**
     * 验证<code>bus.&lt;String&gt;execute(command).getData()</code> 等于 <code>"original"</code>，<code>bus.execute(new CancelOrderCommand())</code> 抛出 <code>IllegalStateException.class</code>，<code>executor.executed</code> 按顺序仅包含 <code>command</code>。
     */
    @Test
    void supportedCommandChangesDoNotAlterAnAlreadyConstructedBus() {
        FixtureExecutor executor = new FixtureExecutor(R.ok("original"), CreateOrderCommand.class);
        CommandBus bus = new DefaultCommandBus(Collections.singletonList(executor));
        executor.commandTypes.clear();
        executor.commandTypes.add(CancelOrderCommand.class);
        CreateOrderCommand command = new CreateOrderCommand();

        assertThat(bus.<String>execute(command).getData()).isEqualTo("original");
        assertThrows(IllegalStateException.class, () -> bus.execute(new CancelOrderCommand()));
        assertThat(executor.executed).containsExactly(command);
    }

    /**
     * 验证<code>originalBus.execute(new CancelOrderCommand())</code> 抛出 <code>IllegalStateException.class</code>，<code>later.executed</code>为空，<code>later.executed</code> 按顺序仅包含 <code>command</code>。
     */
    @Test
    void laterRegistryRegistrationOnlyAppearsInANewBus() {
        CommandRegistry registry = new CommandRegistry();
        registry.register(new FixtureExecutor(R.ok(), CreateOrderCommand.class));
        CommandBus originalBus = new DefaultCommandBus(registry.executors());
        FixtureExecutor later = new FixtureExecutor(R.ok("later"), CancelOrderCommand.class);
        registry.register(later);

        assertThrows(IllegalStateException.class, () -> originalBus.execute(new CancelOrderCommand()));
        assertThat(later.executed).isEmpty();
        CommandBus rebuiltBus = new DefaultCommandBus(registry.executors());
        CancelOrderCommand command = new CancelOrderCommand();
        assertThat(rebuiltBus.<String>execute(command).getData()).isEqualTo("later");
        assertThat(later.executed).containsExactly(command);
    }

    /**
     * 验证<code>bus.execute(null)</code> 抛出 <code>NullPointerException.class</code>，<code>executor.executed</code>为空。
     */
    @Test
    void nullCommandKeepsTheExistingCoreNullPointerContract() {
        FixtureExecutor executor = new FixtureExecutor(R.ok(), CreateOrderCommand.class);
        CommandBus bus = new DefaultCommandBus(Collections.singletonList(executor));

        assertThrows(NullPointerException.class, () -> bus.execute(null));
        assertThat(executor.executed).isEmpty();
    }

    /**
     * 验证被测调用 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void nullExecutorCollectionIsRejectedAtConstruction() {
        assertThrows(NullPointerException.class, () -> new DefaultCommandBus(null));
    }

    /**
     * 验证被测调用 抛出 <code>NullPointerException.class</code>。
     */
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

    /**
     * 用于断言命令类型路由的特殊下单命令。
     */
    private static final class SpecialCreateOrderCommand extends CreateOrderCommand {
    }

    /**
     * 用于验证取消订单命令路由的样本。
     */
    private static final class CancelOrderCommand implements Command {
    }

    /**
     * 用于验证订单发运命令路由的样本。
     */
    private static final class ShipOrderCommand implements Command {
    }

    /**
     * 可观测执行器记录输入，便于发现误路由、重复执行或错误结果转换。
     */
    private static final class FixtureExecutor implements CommandExecutor<Command> {
        /**
         * 夹具执行器声明的命令类型集合。
         */
        private final Set<Class<? extends Command>> commandTypes;
        /**
         * 执行被测操作后保存的结果。
         */
        private final R<?> result;
        /**
         * 标记测试回调或业务操作已经实际执行。
         */
        private final List<Command> executed = new ArrayList<>();

        /**
         * 构造供当前测试使用的 <code>FixtureExecutor</code> 实例。
         *
         * @param result       执行被测操作后保存的结果
         * @param commandTypes 用于初始化 <code>FixtureExecutor</code> 的 <code>Class&lt;? extends Command&gt;[]</code> 值
         */
        @SafeVarargs
        private FixtureExecutor(R<?> result, Class<? extends Command>... commandTypes) {
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
        public R<?> execute(Command command) {
            executed.add(command);
            return result;
        }
    }
}
