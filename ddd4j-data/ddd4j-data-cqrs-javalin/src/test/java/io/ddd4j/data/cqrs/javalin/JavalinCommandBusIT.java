package io.ddd4j.data.cqrs.javalin;

import java.util.Collections;
import java.util.Arrays;
import java.util.LinkedHashSet;
import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * {@link JavalinCommandBus} 真实 Javalin 启动 + ServiceLoader 发现集成测试
 * （Task 6.7）。
 *
 * <p>真实发现链（test classpath 的
 * {@code META-INF/services/io.ddd4j.core.cqrs.command.CommandExecutor} 注册样例执行器，
 * JDK {@link ServiceLoader} 迭代成集合供入 {@code create} 工厂）＋真实执行器服务
 * （零 mock）——完整验证「真实 Javalin 应用启动（{@code start(0)} 随机端口）→
 * ServiceLoader 迭代收集 → CommandRegistry 整批注册 → DefaultCommandBus 快照组装」
 * 的装配链与 ddd4j-core 的分发契约。三用例与 SPI 侧 {@code CommandRegistryTest} 及
 * {@code SpringCommandBusIT}／{@code HelidonCommandBusIT} 对齐：
 * <ol>
 *   <li>execute 返回 Result.success（真实路由到业务 Handler）</li>
 *   <li>多类型执行器之一冲突 → 整批拒绝的 ISE 自工厂传播（同 4.4 注册语义）</li>
 *   <li>未注册命令走 ISE（与 ddd4j-core DefaultCommandBus 一致）</li>
 * </ol>
 *
 * <p>发现轨说明：Javalin 无容器——候选由 JDK SPI 枚举（集成方供入 {@code create}
 * 的 {@code Collection} 即本测试的 ServiceLoader 迭代产物，与生产接法一致）；
 * 总线 {@code execute} 为同步调用，不依赖 Javalin 的 HTTP 回路（IT 不挂任何
 * handler，验证分发与 Web 层解耦）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DisplayName("JavalinCommandBus 真实 Javalin 启动 + ServiceLoader 发现 IT")
class JavalinCommandBusIT {

    /**
     * 真实 Javalin 应用实例（随机端口启动，IT 全程生命周期自管）。
     */
    private Javalin app;

    @BeforeEach
    void startJavalin() {
        app = Javalin.create(cfg -> { }).start(0);
    }

    @AfterEach
    void stopJavalin() {
        if (app != null) {
            app.stop();
        }
    }

    /**
     * 每用例独立装配总线（构造即快照、无共享可变状态），保持用例间隔离。
     * 发现轨为真实 JDK {@link ServiceLoader}（{@link #discover}）。
     *
     * @return 经真实 ServiceLoader 发现装配的总线
     */
    private JavalinCommandBus assemble() {
        return JavalinCommandBus.create(app, discover());
    }

    /**
     * 真实发现轨：迭代 test classpath 的
     * {@code META-INF/services/io.ddd4j.core.cqrs.command.CommandExecutor} 声明成
     * 候选集合（本方法即 raw 泛型收口点，集成方同款接法见
     * {@code JavalinCommandBus} javadoc）。
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
    void execute_已注册命令_应路由到真实Handler并返回成功() {
        JavalinCommandBus bus = assemble();

        Result<String> result = bus.execute(new SampleCommand());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.data()).contains(SampleCommandHandler.HANDLED);
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
                IllegalStateException.class, () -> JavalinCommandBus.create(app, executors));

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).contains(SampleCommand.class.getName());
    }

    @Test
    void execute_未注册命令_应抛ISE与ddd4j_core一致() {
        JavalinCommandBus bus = assemble();

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
            return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(SampleCommand.class, CompanionCommand.class)));
        }

        @Override
        public Result execute(Command command) {
            return Result.ok();
        }
    }
}
