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
package io.ddd4j.data.cqrs.spring;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.CommandRegistry;
import io.ddd4j.core.cqrs.command.Result;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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
@SpringBootTest(classes = TestApp.class, properties = "spring.application.name=ddd4j-runtime-spring-cqrs-it")
class SpringCommandBusIT {

    /** \u5f53\u524d\u6d4b\u8bd5\u6267\u884c\u547d\u4ee4\u6216\u53d1\u5e03\u4e8b\u4ef6\u4f7f\u7528\u7684\u603b\u7ebf\u3002 */
    @Autowired
    private SpringCommandBus bus;

    /** \u9a8c\u8bc1<code>result.isSuccess()</code>\u4e3a\u771f\uff0c<code>result.data()</code> \u5305\u542b <code>SampleCommandHandler.HANDLED</code>\u3002 */
    @Test
    void execute_已注册命令_应路由到真实Handler并返回成功() {
        Result<String> result = bus.execute(new SampleCommand());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.data()).contains(SampleCommandHandler.HANDLED);
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>context.getStartupFailure()</code>\u3001<code>ConflictCommand.class.getName()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
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

    /** \u9a8c\u8bc1<code>exception</code>\u975e\u7a7a\uff0c<code>exception.getMessage()</code> \u5305\u542b <code>UnknownCommand.class.getName()</code>\u3002 */
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

    /** \u4f9b\u547d\u4ee4\u603b\u7ebf\u9a8c\u8bc1\u7c7b\u578b\u5339\u914d\u548c\u5206\u53d1\u7684\u547d\u4ee4\u6837\u672c\u3002 */
    static class ConflictCommand implements Command {
    }

    /** \u4e0e\u4e3b\u8981\u6d4b\u8bd5\u547d\u4ee4\u5e76\u884c\u6ce8\u518c\u7684\u7b2c\u4e8c\u79cd\u547d\u4ee4\u6837\u672c\u3002 */
    static class CompanionCommand implements Command {
    }

    /** \u6ca1\u6709\u6ce8\u518c\u6267\u884c\u5668\u7684\u547d\u4ee4\u6837\u672c\uff0c\u7528\u6765\u9a8c\u8bc1\u547d\u4ee4\u5206\u53d1\u8fb9\u754c\u3002 */
    static class UnknownCommand implements Command {
    }

    /** \u63a5\u6536\u6d4b\u8bd5\u547d\u4ee4\u5e76\u8fd4\u56de\u53ef\u65ad\u8a00\u7ed3\u679c\u7684\u6267\u884c\u5668\u66ff\u8eab\u3002 */
    static class ExclusiveConflictHandler implements CommandExecutor<ConflictCommand> {

        /** {@inheritDoc} */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return Collections.<Class<? extends Command>>singleton(ConflictCommand.class);
        }

        /** {@inheritDoc} */
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

        /** {@inheritDoc} */
        @Override
        public Set<Class<? extends Command>> supportedCommands() {
            return new HashSet<Class<? extends Command>>(Arrays.asList(ConflictCommand.class, CompanionCommand.class));
        }

        /** {@inheritDoc} */
        @Override
        public Result execute(Command command) {
            return Result.ok();
        }
    }
}
