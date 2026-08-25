package io.ddd4j.data.cqrs.helidon;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.data.cqrs.CommandRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * {@link HelidonCommandBus} 真实 ServiceLoader 发现集成测试（Task 6.6）。
 *
 * <p>真实发现链（test classpath 的
 * {@code META-INF/services/io.ddd4j.core.cqrs.command.CommandExecutor} 注册样例执行器，
 * {@code HelidonServiceLoader} 包装 JDK {@link ServiceLoader} 迭代）＋真实执行器服务
 * （零 mock）——完整验证「{@code HelidonServiceLoader} 迭代收集 → CommandRegistry
 * 整批注册 → DefaultCommandBus 快照组装」的装配链与 ddd4j-core 的分发契约。
 * 三用例与 SPI 侧 {@code CommandRegistryTest} 及 {@code SpringCommandBusIT}／
 * {@code QuarkusCommandBusIT}／{@code MicronautCommandBusIT} 对齐：
 * <ol>
 *   <li>execute 返回 Result.success（真实路由到业务 Handler）</li>
 *   <li>多类型执行器之一冲突 → 整批拒绝的 ISE 自构造器传播（同 4.4 注册语义）</li>
 *   <li>未注册命令走 ISE（与 ddd4j-core DefaultCommandBus 一致）</li>
 * </ol>
 *
 * <p>冲突轨说明：以「预注册多类型占位执行器的注册中心＋真实 ServiceLoader 发现」
 * 承载冲突对——注册／快照语义走真实 {@code CommandRegistry}＋
 * {@code DefaultCommandBus} 构造链，发现轨仍为真实
 * {@code HelidonServiceLoader}（发现到 services 文件中的样例执行器即撞型）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DisplayName("HelidonCommandBus 真实 ServiceLoader 发现 IT")
class HelidonCommandBusIT {

    /**
     * 每用例独立装配总线（构造即快照、无共享可变状态），保持用例间隔离。
     * 走 {@link HelidonCommandBus#discover} 便捷工厂（raw 泛型收口点），
     * 发现链仍为真实 {@code HelidonServiceLoader}＋JDK {@link ServiceLoader}。
     *
     * @return 经真实 ServiceLoader 发现装配的总线
     */
    private HelidonCommandBus assemble() {
        return HelidonCommandBus.discover(new CommandRegistry());
    }

    @Test
    void execute_已注册命令_应路由到真实Handler并返回成功() {
        HelidonCommandBus bus = assemble();

        Result<String> result = bus.execute(new SampleCommand());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.data()).contains(SampleCommandHandler.HANDLED);
    }

    @Test
    void 装配期冲突_整批拒绝应自构造器传播() {
        // 预注册多类型占位执行器（SampleCommand + CompanionCommand），再以真实
        // ServiceLoader 发现装配总线：发现到 services 文件中的 SampleCommandHandler
        // 即与占位方在 SampleCommand 撞型 → CommandRegistry 整批拒绝的 ISE 自构造器
        // 直接传播（装配期立即暴露，未拦截）。
        CommandRegistry registry = new CommandRegistry();
        registry.register(new OverlappingMultiTypeHandler());

        IllegalStateException exception = catchThrowableOfType(
                IllegalStateException.class, () -> HelidonCommandBus.discover(registry));

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).contains(SampleCommand.class.getName());
    }

    @Test
    void execute_未注册命令_应抛ISE与ddd4j_core一致() {
        HelidonCommandBus bus = assemble();

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
     * {@link SampleCommandHandler} 在 SampleCommand 撞型，验证后注册方被整批拒绝
     * （CompanionCommand 一并不落库）且 ISE 自构造器传播。刻意不注册进
     * services 文件（占位方经注册中心直入，撞型由发现轨触发）。
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
