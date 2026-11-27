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
package io.ddd4j.vertx.command;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * {@link VertxCommandBus} 真实 Vert.x 实例生命周期 + ServiceLoader 发现集成测试
 * （Task 6.8）。
 *
 * <p>真实发现链（test classpath 的
 * {@code META-INF/services/io.ddd4j.core.cqrs.command.CommandExecutor} 注册样例执行器，
 * JDK {@link ServiceLoader} 迭代成集合供入 {@code create} 工厂）＋真实执行器服务
 * （零 mock）——完整验证「真实 {@code Vertx.vertx()} 实例生命周期（close in
 * AfterEach）→ ServiceLoader 迭代收集 → CommandRegistry 整批注册 →
 * DefaultCommandBus 快照组装」的装配链与 ddd4j-core 的分发契约。三用例与 SPI 侧
 * {@code CommandRegistryTest} 及 {@code SpringCommandBusTest}／
 * {@code HelidonCommandBusTest} 对齐：
 * <ol>
 *   <li>execute 在 Vert.x 上下文线程同步返回 Result.success（真实路由到业务
 *       Handler——证明 DefaultCommandBus 的同步分发不依赖 Vert.x 异步回路）</li>
 *   <li>多类型执行器之一冲突 → 整批拒绝的 ISE 自工厂传播（同 4.4 注册语义）</li>
 *   <li>未注册命令走 ISE（与 ddd4j-core DefaultCommandBus 一致）</li>
 * </ol>
 *
 * <p>发现轨说明：Vert.x 无容器——候选由 JDK SPI 枚举（集成方供入 {@code create}
 * 的 {@code Collection} 即本测试的 ServiceLoader 迭代产物，与生产接法一致）；
 * {@link CompletableFuture} 将上下文线程的结果交给 JUnit 断言。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DisplayName("VertxCommandBus 真实 Vert.x 实例生命周期 + ServiceLoader 发现 IT")
class VertxCommandBusTest {

    /**
     * 真实 Vert.x 实例（{@code Vertx.vertx()} 工厂获取，IT 全程生命周期自管）。
     */
    private Vertx vertx;

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>Vertx.vertx()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void startVertx() {
        vertx = Vertx.vertx();
    }

    /**
     * \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>vertx.close()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @AfterEach
    void closeVertx() throws Exception {
        if (vertx != null) {
            // Vert.x 5：close() 返回 Future<Void>，同步等待真实关闭完成
            // （不泄漏事件循环线程到下一用例）。
            vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }

    /**
     * 每用例独立装配总线（构造即快照、无共享可变状态），保持用例间隔离。
     * 发现轨为真实 JDK {@link ServiceLoader}（{@link #discover}）。
     *
     * @return 经真实 ServiceLoader 发现装配的总线
     */
    private VertxCommandBus assemble() {
        return VertxCommandBus.create(vertx, discover());
    }

    /**
     * 真实发现轨：迭代 test classpath 的
     * {@code META-INF/services/io.ddd4j.core.cqrs.command.CommandExecutor} 声明成
     * 候选集合（本方法即 raw 泛型收口点，集成方同款接法见
     * {@code VertxCommandBus} javadoc）。
     *
     * @return ServiceLoader 发现的执行器候选集合
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<CommandExecutor<?>> discover() {
        List<CommandExecutor<?>> executors = new ArrayList<>();
        for (CommandExecutor executor : ServiceLoader.load(CommandExecutor.class)) {
            executors.add(executor);
        }
        return executors;
    }

    /**
     * \u9a8c\u8bc1<code>Vertx.currentContext().isEventLoopContext()</code>\u4e3a\u771f\uff0c<code>result.isSuccess()</code>\u4e3a\u771f\uff0c<code>result.data()</code> \u5305\u542b <code>SampleCommandHandler.HANDLED</code>\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void execute_已在Vertx上下文线程同步执行_应路由到真实Handler并返回成功() throws Exception {
        VertxCommandBus bus = assemble();
        CompletableFuture<Result<String>> completion = new CompletableFuture<>();
        vertx.runOnContext(ignored -> {
            try {
                assertThat(Vertx.currentContext().isEventLoopContext()).isTrue();
                completion.complete(bus.execute(new SampleCommand()));
            } catch (Throwable failure) {
                completion.completeExceptionally(failure);
            }
        });

        Result<String> result = completion.get(5, TimeUnit.SECONDS);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.data()).contains(SampleCommandHandler.HANDLED);
    }

    /** \u9a8c\u8bc1<code>exception</code>\u975e\u7a7a\uff0c<code>exception.getMessage()</code> \u5305\u542b <code>SampleCommand.class.getName()</code>\u3002 */
    @Test
    void 装配期冲突_整批拒绝应自工厂传播() {
        // 供入「多类型占位执行器（SampleCommand + CompanionCommand）+ 真实
        // ServiceLoader 发现的候选集合」：占位方与 services 文件中的
        // SampleCommandHandler 在 SampleCommand 撞型 → CommandRegistry 整批拒绝的
        // ISE 自 create 工厂直接传播（装配期立即暴露，未拦截）。
        List<CommandExecutor<?>> executors = new ArrayList<>();
        executors.add(new OverlappingMultiTypeHandler());
        executors.addAll(discover());

        IllegalStateException exception = catchThrowableOfType(
                IllegalStateException.class, () -> VertxCommandBus.create(vertx, executors));

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).contains(SampleCommand.class.getName());
    }

    /** \u9a8c\u8bc1<code>exception</code>\u975e\u7a7a\uff0c<code>exception.getMessage()</code> \u5305\u542b <code>UnknownCommand.class.getName()</code>\u3002 */
    @Test
    void execute_未注册命令_应抛ISE与ddd4j_core一致() {
        VertxCommandBus bus = assemble();

        IllegalStateException exception = catchThrowableOfType(
                IllegalStateException.class, () -> bus.execute(new UnknownCommand()));

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).contains(UnknownCommand.class.getName());
    }

    /** \u9a8c\u8bc1<code>bus.execute(new SampleCommand()).isSuccess()</code>\u4e3a\u771f\u3002 */
    @Test
    void factoryCapturesSnapshotAndRejectsRepeatedSameExecutor() {
        List<CommandExecutor<?>> candidates = new ArrayList<>(discover());
        VertxCommandBus bus = VertxCommandBus.create(vertx, candidates);
        candidates.clear();
        assertThat(bus.execute(new SampleCommand()).isSuccess()).isTrue();
        CommandExecutor<?> same = discover().get(0);
        assertThat(catchThrowableOfType(IllegalStateException.class,
                () -> VertxCommandBus.create(vertx, Arrays.asList(same, same))))
                .hasMessageContaining(SampleCommand.class.getName());
    }

    /** \u9a8c\u8bc1<code>first</code> \u7b49\u4e8e <code>second</code>\uff0c<code>bus.&lt;String&gt;execute(new SampleCommand()).data()</code> \u5305\u542b <code>"sample"</code>\uff0c<code>bus.&lt;String&gt;execute(new CompanionCommand()).data()</code> \u5305\u542b <code>"companion"</code>\u3002 */
    @Test
    void routesDistinctCommandTypesEvenWhenExecutorsCompareEqual() {
        CommandExecutor<?> first = new EqualExecutor(SampleCommand.class, "sample");
        CommandExecutor<?> second = new EqualExecutor(CompanionCommand.class, "companion");
        assertThat(first).isEqualTo(second);

        VertxCommandBus bus = VertxCommandBus.create(vertx, Arrays.asList(first, second));

        assertThat(bus.<String>execute(new SampleCommand()).data()).contains("sample");
        assertThat(bus.<String>execute(new CompanionCommand()).data()).contains("companion");
    }

    /** \u63a5\u6536\u6d4b\u8bd5\u547d\u4ee4\u5e76\u8fd4\u56de\u53ef\u65ad\u8a00\u7ed3\u679c\u7684\u6267\u884c\u5668\u66ff\u8eab\u3002 */
    private static final class EqualExecutor implements CommandExecutor<Command> {
        /** \u9009\u62e9\u6d4b\u8bd5\u547d\u4ee4\u6267\u884c\u5668\u65f6\u4f7f\u7528\u7684\u547d\u4ee4\u7c7b\u578b\u3002 */
        private final Class<? extends Command> commandType;
        /** \u6267\u884c\u88ab\u6d4b\u64cd\u4f5c\u540e\u4fdd\u5b58\u7684\u7ed3\u679c\u3002 */
        private final String result;

        /**
         * \u8bbe\u7f6e <code>commandType</code>\u3001<code>result</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>EqualExecutor</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param commandType \u7528\u4e8e\u521d\u59cb\u5316 <code>EqualExecutor</code> \u7684 <code>Class&lt;? extends Command&gt;</code> \u503c
         * @param result \u6267\u884c\u88ab\u6d4b\u64cd\u4f5c\u540e\u4fdd\u5b58\u7684\u7ed3\u679c
         */
        private EqualExecutor(Class<? extends Command> commandType, String result) {
            this.commandType = commandType;
            this.result = result;
        }

        /**
         * 声明当前处理器可以接收的命令类型。
         *
         * <p>测试夹具会调用 <code>Collections.singleton(commandType)</code>。</p>
         *
         * @return 实际返回<code>Collections.singleton(commandType)</code>
         */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.singleton(commandType);
        }

        /**
         * 用当前处理器执行传入命令或操作。
         *
         * <p>测试夹具会调用 <code>Result.ok(result)</code>。</p>
         *
         * @param command 命令对象
         * @return 实际返回<code>Result.ok(result)</code>
         */
        @Override
        public Result<String> execute(Command command) {
            return Result.ok(result);
        }

        /**
         * 比较两个对象的值是否相等。
         *
         * @param other 与当前对象比较的对象
         * @return 对象满足此实现的相等条件时为真
         */
        @Override
        public boolean equals(Object other) {
            return other instanceof EqualExecutor;
        }

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            return EqualExecutor.class.hashCode();
        }
    }

    /** 用于验证多类型处理器撞型拒绝的伴随命令。 */
    static class CompanionCommand implements Command {
    }

    /** 用于验证未注册命令错误路径的测试命令。 */
    static class UnknownCommand implements Command {
    }

    /**
     * 多类型占位执行器（SampleCommand + CompanionCommand）：与 services 文件中的
     * {@link SampleCommandHandler} 在 SampleCommand 撞型，验证后供入方被整批拒绝
     * （CompanionCommand 一并不落库）且 ISE 自工厂传播。刻意不注册进
     * services 文件（占位方经 {@code create} 的 Collection 直入，撞型由发现轨触发）。
     */
    static class OverlappingMultiTypeHandler implements CommandExecutor<Command> {

        /**
         * 声明当前处理器可以接收的命令类型。
         *
         * <p>测试夹具会调用 <code>Arrays.asList(SampleCommand.class, CompanionCommand.class)</code>。</p>
         *
         * @return 支持的命令类型集合
         */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(SampleCommand.class, CompanionCommand.class)));
        }

        /**
         * 执行占位命令。
         *
         * @param command 测试命令
         * @return 成功结果
         */
        @Override
        public Result execute(Command command) {
            return Result.ok();
        }
    }
}
