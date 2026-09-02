package io.ddd4j.data.cqrs.vertx;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
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
 * {@code CommandRegistryTest} 及 {@code SpringCommandBusIT}／
 * {@code HelidonCommandBusIT} 对齐：
 * <ol>
 *   <li>execute 在 Vert.x 上下文线程同步返回 Result.success（真实路由到业务
 *       Handler——证明 DefaultCommandBus 的同步分发不依赖 Vert.x 异步回路）</li>
 *   <li>多类型执行器之一冲突 → 整批拒绝的 ISE 自工厂传播（同 4.4 注册语义）</li>
 *   <li>未注册命令走 ISE（与 ddd4j-core DefaultCommandBus 一致）</li>
 * </ol>
 *
 * <p>发现轨说明：Vert.x 无容器——候选由 JDK SPI 枚举（集成方供入 {@code create}
 * 的 {@code Collection} 即本测试的 ServiceLoader 迭代产物，与生产接法一致）；
 * {@link VertxTestContext} 仅作 Vert.x 上下文线程到 JUnit 的异步桥（非总线依赖）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DisplayName("VertxCommandBus 真实 Vert.x 实例生命周期 + ServiceLoader 发现 IT")
class VertxCommandBusIT {

    /**
     * 真实 Vert.x 实例（{@code Vertx.vertx()} 工厂获取，IT 全程生命周期自管）。
     */
    private Vertx vertx;

    @BeforeEach
    void startVertx() {
        vertx = Vertx.vertx();
    }

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

    @Test
    void execute_已在Vertx上下文线程同步执行_应路由到真实Handler并返回成功() throws Exception {
        VertxCommandBus bus = assemble();
        VertxTestContext testContext = new VertxTestContext();

        // 装配在 JUnit 线程、分发在 Vert.x 上下文线程：execute 同步返回 Result，
        // 不经过 Future 回路——验证 DefaultCommandBus 的同步性不依赖 Vert.x 异步链。
        vertx.runOnContext(ignored -> {
            Result<String> result = bus.execute(new SampleCommand());
            testContext.verify(() -> {
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.data()).contains(SampleCommandHandler.HANDLED);
            });
            testContext.completeNow();
        });

        assertThat(testContext.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(testContext.failed()).isFalse();
    }

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

    @Test
    void execute_未注册命令_应抛ISE与ddd4j_core一致() {
        VertxCommandBus bus = assemble();

        IllegalStateException exception = catchThrowableOfType(
                IllegalStateException.class, () -> bus.execute(new UnknownCommand()));

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).contains(UnknownCommand.class.getName());
    }

    static class CompanionCommand implements Command {
    }

    static class UnknownCommand implements Command {
    }

    /**
     * 多类型占位执行器（SampleCommand + CompanionCommand）：与 services 文件中的
     * {@link SampleCommandHandler} 在 SampleCommand 撞型，验证后供入方被整批拒绝
     * （CompanionCommand 一并不落库）且 ISE 自工厂传播。刻意不注册进
     * services 文件（占位方经 {@code create} 的 Collection 直入，撞型由发现轨触发）。
     */
    static class OverlappingMultiTypeHandler implements CommandExecutor<Command> {

        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Set.of(SampleCommand.class, CompanionCommand.class);
        }

        @Override
        public Result execute(Command command) {
            return Result.ok();
        }
    }
}
