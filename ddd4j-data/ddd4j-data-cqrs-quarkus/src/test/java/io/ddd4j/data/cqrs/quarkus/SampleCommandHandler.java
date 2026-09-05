package io.ddd4j.data.cqrs.quarkus;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.data.cqrs.CommandHandler;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

/**
 * 样例命令处理器（集成测试专用）：模拟集成方业务中的执行器——
 * {@code @ApplicationScoped} 刻板（ArC 发现键）＋{@code @CommandHandler} 发现注解
 * （声明性元数据，见 {@code QuarkusCommandBus} javadoc 的发现等价性注记），
 * 由真实 ArC 容器在索引中发现装配。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@ApplicationScoped
@CommandHandler(SampleCommand.class)
class SampleCommandHandler implements CommandExecutor<SampleCommand> {

    /**
     * execute 返回的载荷标记（IT 断言「真实路由到本 Handler」用）。
     */
    static final String HANDLED = "handled-by-quarkus-handler";

    @Override
    public Set<Class<? extends Command>> supportedCommands() {
        return Set.of(SampleCommand.class);
    }

    @Override
    public Result execute(SampleCommand command) {
        return Result.ok(HANDLED);
    }
}
