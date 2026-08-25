package io.ddd4j.data.cqrs.micronaut;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.data.cqrs.CommandRegistry;
import io.micronaut.context.BeanContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * {@link MicronautCommandBus} 真实容器集成测试（Task 6.5）。
 *
 * <p>真实 Micronaut 容器（{@code @MicronautTest} 引导完整 {@code BeanContext}，
 * Bean 定义由 {@code micronaut-inject-java} 在编译期生成——见
 * {@code MicronautCommandBus} javadoc 的发现注记）＋真实 {@code @Singleton}
 * 执行器 Bean（零 mock）——完整验证「构造期 {@code BeanContext.getBeansOfType}
 * 收集 → CommandRegistry 整批注册 → DefaultCommandBus 快照组装」的装配链与
 * ddd4j-core 的分发契约。三用例与 SPI 侧 {@code CommandRegistryTest} 及
 * {@code SpringCommandBusIT}／{@code QuarkusCommandBusIT} 对齐：
 * <ol>
 *   <li>execute 返回 Result.success（真实路由到业务 Handler）</li>
 *   <li>多类型执行器之一冲突 → 整批拒绝的 ISE 自构造器传播（同 4.4 注册语义）</li>
 *   <li>未注册命令走 ISE（与 ddd4j-core DefaultCommandBus 一致）</li>
 * </ol>
 *
 * <p>冲突轨说明：单个 {@code @MicronautTest} 只引导一个应用，若把冲突执行器注册
 * 为真实 Bean 会拖垮所有用例的总线装配，故以「预注册占位执行器的注册中心＋真实
 * 容器收集」承载冲突对——注册／快照语义走真实 {@code CommandRegistry}＋
 * {@code DefaultCommandBus} 构造链，容器轨仍为真实 {@code BeanContext} 收集。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DisplayName("MicronautCommandBus 真实容器 IT")
@MicronautTest
class MicronautCommandBusIT {

    @Inject
    MicronautCommandBus bus;

    @Inject
    BeanContext context;

    @Test
    void execute_已注册命令_应路由到真实Handler并返回成功() {
        Result<String> result = bus.execute(new SampleCommand());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.data()).contains(SampleCommandHandler.HANDLED);
    }

    @Test
    void 装配期冲突_整批拒绝应自构造器传播() {
        // 预注册多类型占位执行器（SampleCommand + CompanionCommand），再以真实容器
        // 构造总线：collect 收集到容器中的 SampleCommandHandler 即与占位方在
        // SampleCommand 撞型 → CommandRegistry 整批拒绝的 ISE 自构造器直接传播
        // （装配期立即暴露，未拦截；不污染共享总线 Bean——本用例局部构造）。
        CommandRegistry registry = new CommandRegistry();
        registry.register(new OverlappingMultiTypeHandler());

        IllegalStateException exception = catchThrowableOfType(
                IllegalStateException.class, () -> new MicronautCommandBus(context, registry));

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).contains(SampleCommand.class.getName());
    }

    @Test
    void execute_未注册命令_应抛ISE与ddd4j_core一致() {
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
     * 多类型占位执行器（SampleCommand + CompanionCommand）：与容器中的
     * {@link SampleCommandHandler} 在 SampleCommand 撞型，验证后注册方被整批拒绝
     * （CompanionCommand 一并不落库）且 ISE 自构造器传播。
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
