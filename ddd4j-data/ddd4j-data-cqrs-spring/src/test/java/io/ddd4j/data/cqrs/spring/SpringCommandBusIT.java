package io.ddd4j.data.cqrs.spring;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.data.cqrs.CommandRegistry;
import io.ddd4j.data.cqrs.sample.SampleCommand;
import io.ddd4j.data.cqrs.sample.SampleCommandHandler;
import io.ddd4j.data.cqrs.sample.TestApp;
import io.ddd4j.data.cqrs.sample.TxProbeCommand;
import io.ddd4j.data.cqrs.sample.TxProbeCommandHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * {@link SpringCommandBus} 真实容器集成测试（Task 6.3）。
 *
 * <p>真实 Spring 容器（{@code @SpringBootTest} 引导 {@code TestApp}，集成方姿势
 * 的最小装配）＋真实 {@code @Component} 执行器 Bean（零 mock）——完整验证
 * 「构造期收集 → CommandRegistry 整批注册 → DefaultCommandBus 快照组装」的
 * 装配链与 ddd4j-core 的分发契约。前三用例与 SPI 侧 {@code CommandRegistryTest}
 * 及 {@code QuarkusCommandBusIT}（-quarkus 模块）对齐：
 * <ol>
 *   <li>execute 返回 Result.success（真实路由到业务 Handler）</li>
 *   <li>多类型执行器之一冲突 → 整批拒绝以容器启动失败传播（同 4.4 注册语义）</li>
 *   <li>未注册命令走 ISE（与 ddd4j-core DefaultCommandBus 一致）</li>
 * </ol>
 * 第四用例为事务生效证明（Task 6.3 修复轮）：方法级 {@code @Transactional}
 * 于 {@code execute()} 经事务代理在活动事务中分发（TestApp 注册
 * NoopTransactionManager 激活代理）。
 *
 * <p>warning: spring-testcontext 每配置缓存一次上下文，启动开销秒级（含
 * 自动配置探测），本类两份上下文（TestApp＋冲突 Runner）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DisplayName("SpringCommandBus 真实容器 IT")
@SpringBootTest(classes = TestApp.class)
class SpringCommandBusIT {

    @Autowired
    private SpringCommandBus bus;

    @Test
    void execute_已注册命令_应路由到真实Handler并返回成功() {
        Result<String> result = bus.execute(new SampleCommand());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.data()).contains(SampleCommandHandler.HANDLED);
    }

    @Test
    void 装配期冲突_整批拒绝应传播为容器启动失败() {
        // 刻意不用嵌套 @Configuration：TestApp 的 @ComponentScan("io.ddd4j.data.cqrs")
        // 会把本包测试类路径上的嵌套配置类一并扫入主容器（Bean 定义撞名），
        // 故以 withBean 显式注册（不经扫描）构造孤立冲突容器。
        new ApplicationContextRunner()
                .withBean(CommandRegistry.class)
                .withBean(SpringCommandBus.class)
                .withBean(ExclusiveConflictHandler.class)
                .withBean(OverlappingMultiTypeHandler.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    // CommandRegistry 整批拒绝的 ISE 自 SpringCommandBus 构造器
                    // 沿 Bean 创建链传播为容器启动失败（装配期立即暴露）
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .rootCause()
                            .hasMessageContaining(ConflictCommand.class.getName());
                });
    }

    @Test
    void execute_未注册命令_应抛ISE与ddd4j_core一致() {
        IllegalStateException exception = catchThrowableOfType(
                IllegalStateException.class, () -> bus.execute(new UnknownCommand()));

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).contains(UnknownCommand.class.getName());
    }

    /**
     * 事务生效证明（Task 6.3 修复轮）：方法级 {@code @Transactional} 于
     * {@code SpringCommandBus.execute()}（纯委托 override）必须真实包裹分发。
     * 双断言：①注入的总线是 AOP 代理（切面挂靠成功）；②事务探针 Handler 在
     * {@code execute} 内观察到活动事务（事务边界真实落在分发路径上）。历史缺陷
     * （类级注解对继承方法不生效——属性查找只看方法自身与声明类 DefaultCommandBus）
     * 时本用例两断言皆失败：无代理、无活动事务。
     */
    @Test
    void execute_方法级Transactional_应经事务代理在活动事务中分发() {
        assertThat(AopUtils.isAopProxy(bus))
                .as("存在 PlatformTransactionManager 时 SpringCommandBus 应被事务切面代理")
                .isTrue();

        bus.execute(new TxProbeCommand());

        assertThat(TxProbeCommandHandler.DISPATCHED_IN_ACTIVE_TX.get())
                .as("事务探针 Handler 应在活动事务内执行（isActualTransactionActive）")
                .isTrue();
    }

    static class ConflictCommand implements Command {
    }

    static class CompanionCommand implements Command {
    }

    static class UnknownCommand implements Command {
    }

    static class ExclusiveConflictHandler implements CommandExecutor<ConflictCommand> {

        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Set.of(ConflictCommand.class);
        }

        @Override
        public Result execute(ConflictCommand command) {
            return Result.ok();
        }
    }

    /**
     * 多类型执行器（ConflictCommand + CompanionCommand）：与
     * {@link ExclusiveConflictHandler} 在 ConflictCommand 撞型，验证后注册方
     * 被整批拒绝（CompanionCommand 一并不落库）且传播为启动失败。
     */
    static class OverlappingMultiTypeHandler implements CommandExecutor<Command> {

        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Set.of(ConflictCommand.class, CompanionCommand.class);
        }

        @Override
        public Result execute(Command command) {
            return Result.ok();
        }
    }
}
