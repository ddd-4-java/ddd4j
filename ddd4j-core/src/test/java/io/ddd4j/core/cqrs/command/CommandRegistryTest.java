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

import io.ddd4j.core.api.R;
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

    /**
     * 当前用例注册被测组件和观察指标的容器。
     */
    private final CommandRegistry registry = new CommandRegistry();

    /**
     * 验证<code>found</code> 与…为同一实例 <code>executor</code>。
     */
    @Test
    void registeredExecutorIsFoundByExactCommandType() {
        CreateOrderCmdExe executor = new CreateOrderCmdExe();

        registry.register(executor);

        CommandExecutor<CreateOrderCommand> found = registry.findExecutor(CreateOrderCommand.class);
        assertSame(executor, found, "findExecutor should return the registered executor instance");
    }

    /**
     * 验证<code>registry.findExecutor(CancelOrderCommand.class)</code>为空。
     */
    @Test
    void findExecutorReturnsNullForUnregisteredCommandType() {
        registry.register(new CreateOrderCmdExe());

        assertNull(registry.findExecutor(CancelOrderCommand.class),
                "unregistered command type should yield null (bus layer owns the missing-executor error)");
    }

    /**
     * 验证<code>registry.register(null)</code> 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void registerRejectsNullExecutor() {
        assertThrows(NullPointerException.class, () -> registry.register(null));
    }

    /**
     * 验证<code>registry.register(new CreateOrderCmdExe())</code> 抛出 <code>IllegalStateException.class</code>，<code>ex</code> 的异常消息包含 <code>CreateOrderCommand.class.getName()</code>。
     */
    @Test
    void duplicateCommandTypeRegistrationThrowsIllegalStateWithCommandName() {
        registry.register(new CreateOrderCmdExe());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.register(new CreateOrderCmdExe()));

        assertThat(ex).hasMessageContaining(CreateOrderCommand.class.getName());
    }

    /**
     * 验证<code>registry.register(multiTypeExecutor)</code> 抛出 <code>IllegalStateException.class</code>，<code>ex</code> 的异常消息包含 <code>CreateOrderCommand.class.getName()</code>，<code>registry.executors()</code> 的大小为 <code>1</code>。
     */
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

    /**
     * 验证<code>registry.executors().add(new CancelOrderCmdExe())</code> 抛出 <code>UnsupportedOperationException.class</code>。
     */
    @Test
    void executorsViewIsImmutable() {
        registry.register(new CreateOrderCmdExe());

        assertThrows(UnsupportedOperationException.class,
                () -> registry.executors().add(new CancelOrderCmdExe()));
    }

    /**
     * 验证<code>registry.findExecutor(CreateOrderCommand.class)</code> 与…为同一实例 <code>createExecutor</code>，<code>registry.findExecutor(CancelOrderCommand.class)</code> 与…为同一实例 <code>cancelExecutor</code>，<code>registry.executors()</code> 的大小为 <code>3</code>。
     */
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

    /**
     * 验证<code>snapshot</code> 按顺序仅包含 <code>original</code>，<code>registry.executors()</code> 仅包含 <code>original</code>、<code>later</code>。
     */
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

    /**
     * 验证<code>registry.register(original)</code> 抛出 <code>IllegalStateException.class</code>，<code>registry.findExecutor(CreateOrderCommand.class)</code> 与…为同一实例 <code>original</code>，<code>registry.executors()</code> 按顺序仅包含 <code>original</code>。
     */
    @Test
    void registeringTheSameExecutorTwicePreservesTheOriginalRegistration() {
        CreateOrderCmdExe original = new CreateOrderCmdExe();
        registry.register(original);

        assertThrows(IllegalStateException.class, () -> registry.register(original));

        assertSame(original, registry.findExecutor(CreateOrderCommand.class));
        assertThat(registry.executors()).containsExactly(original);
    }

    /**
     * 验证<code>registry.findExecutor(null)</code> 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void findExecutorRejectsNullCommandType() {
        assertThrows(NullPointerException.class, () -> registry.findExecutor(null));
    }

    /**
     * 供命令总线验证类型匹配和分发的命令样本。
     */
    static class CreateOrderCommand implements Command {
    }

    /**
     * 用于验证取消订单命令路由的样本。
     */
    static class CancelOrderCommand implements Command {
    }

    /**
     * 用于验证订单发运命令路由的样本。
     */
    static class ShipOrderCommand implements Command {
    }

    /**
     * 供命令总线验证类型匹配和分发的命令样本。
     */
    static class TrackOrderCommand implements Command {
    }

    /**
     * 接收测试命令并返回可断言结果的执行器替身。
     */
    static class CreateOrderCmdExe implements CommandExecutor<CreateOrderCommand> {

        /**
         * 声明当前处理器可以接收的命令类型。
         *
         * <p>测试夹具会调用 <code>Collections.&lt;Class&lt;? extends Command&gt;&gt;singleton(CreateOrderCommand.class)</code>。</p>
         *
         * @return 实际返回<code>Collections.&lt;Class&lt;? extends Command&gt;&gt;singleton(CreateOrderCommand.class)</code>
         */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.<Class<? extends Command>>singleton(CreateOrderCommand.class);
        }

        /**
         * 用当前处理器执行传入命令或操作。
         *
         * <p>测试夹具会调用 <code>R.ok()</code>。</p>
         *
         * @param command 命令对象
         * @return 实际返回<code>R.ok()</code>
         */
        @Override
        public R<?> execute(CreateOrderCommand command) {
            return R.ok();
        }
    }

    /**
     * 接收测试命令并返回可断言结果的执行器替身。
     */
    static class CancelOrderCmdExe implements CommandExecutor<CancelOrderCommand> {

        /**
         * 声明当前处理器可以接收的命令类型。
         *
         * <p>测试夹具会调用 <code>Collections.&lt;Class&lt;? extends Command&gt;&gt;singleton(CancelOrderCommand.class)</code>。</p>
         *
         * @return 实际返回<code>Collections.&lt;Class&lt;? extends Command&gt;&gt;singleton(CancelOrderCommand.class)</code>
         */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.<Class<? extends Command>>singleton(CancelOrderCommand.class);
        }

        /**
         * 用当前处理器执行传入命令或操作。
         *
         * <p>测试夹具会调用 <code>R.fail("cancelled")</code>。</p>
         *
         * @param command 命令对象
         * @return 实际返回<code>R.fail("cancelled")</code>
         */
        @Override
        public R<?> execute(CancelOrderCommand command) {
            return R.fail("cancelled");
        }
    }

    /**
     * 声明多命令类型的执行器（CreateOrder + CancelOrder），
     * 用于验证整批拒绝语义：与 CreateOrderCmdExe 冲突时 CancelOrder 也不落库。
     */
    static class OrderLifecycleCmdExe implements CommandExecutor<Command> {

        /**
         * 声明当前处理器可以接收的命令类型。
         *
         * <p>测试夹具会调用 <code>Arrays.&lt;Class&lt;? extends Command&gt;&gt;asList(CancelOrderCommand.class, CreateOrderCommand.class)</code>。</p>
         *
         * @return 支持的命令类型集合
         */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return new LinkedHashSet<>(Arrays.<Class<? extends Command>>asList(
                    CancelOrderCommand.class, CreateOrderCommand.class));
        }

        /**
         * 用当前处理器执行传入命令或操作。
         *
         * <p>测试夹具会调用 <code>R.ok()</code>。</p>
         *
         * @param command 命令对象
         * @return 实际返回<code>R.ok()</code>
         */
        @Override
        public R<?> execute(Command command) {
            return R.ok();
        }
    }

    /**
     * 声明多命令类型的执行器（ShipOrder + TrackOrder），
     * 用于验证多类型路由与「executors() 中多类型执行器只出现一次」。
     */
    static class OrderFulfillmentCmdExe implements CommandExecutor<Command> {

        /**
         * 声明当前处理器可以接收的命令类型。
         *
         * <p>测试夹具会调用 <code>Arrays.&lt;Class&lt;? extends Command&gt;&gt;asList(ShipOrderCommand.class, TrackOrderCommand.class)</code>。</p>
         *
         * @return 支持的命令类型集合
         */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return new HashSet<>(Arrays.<Class<? extends Command>>asList(
                    ShipOrderCommand.class, TrackOrderCommand.class));
        }

        /**
         * 用当前处理器执行传入命令或操作。
         *
         * <p>测试夹具会调用 <code>R.ok()</code>。</p>
         *
         * @param command 命令对象
         * @return 实际返回<code>R.ok()</code>
         */
        @Override
        public R<?> execute(Command command) {
            return R.ok();
        }
    }
}
