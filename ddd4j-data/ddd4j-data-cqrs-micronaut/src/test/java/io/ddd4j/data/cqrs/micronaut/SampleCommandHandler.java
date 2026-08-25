package io.ddd4j.data.cqrs.micronaut;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.data.cqrs.CommandHandler;
import jakarta.inject.Singleton;

import java.util.Set;

/**
 * 样例命令处理器（集成测试专用）：模拟集成方业务中的执行器——
 * {@code @Singleton} 刻板（Micronaut 编译期 Bean 定义发现键）＋
 * {@code @CommandHandler} 发现注解（声明性元数据，见
 * {@code MicronautCommandBus} javadoc 的发现等价性注记），
 * 由 {@code micronaut-inject-java} 在 test 编译期生成 Bean 定义后
 * 被真实 {@code BeanContext} 装配。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Singleton
@CommandHandler(SampleCommand.class)
class SampleCommandHandler implements CommandExecutor<SampleCommand> {

    /**
     * execute 返回的载荷标记（IT 断言「真实路由到本 Handler」用）。
     */
    static final String HANDLED = "handled-by-micronaut-handler";

    @Override
    public Set<Class<? extends Command>> supportedCommands() {
        return Set.of(SampleCommand.class);
    }

    @Override
    public Result execute(SampleCommand command) {
        return Result.ok(HANDLED);
    }
}
