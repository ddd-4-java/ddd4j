package io.ddd4j.data.cqrs.sample;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.data.cqrs.CommandHandler;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 样例命令处理器（集成测试专用）：模拟集成方业务包中的执行器——
 * {@code @Component} 刻板（Spring 发现键）＋{@code @CommandHandler} 发现注解
 * （声明性元数据），由真实 Spring 容器经 {@code TestApp} 的组件扫描装配。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Component
@CommandHandler(SampleCommand.class)
public class SampleCommandHandler implements CommandExecutor<SampleCommand> {

    /**
     * execute 返回的载荷标记（IT 断言「真实路由到本 Handler」用）。
     */
    public static final String HANDLED = "handled-by-sample-handler";

    @Override
    public Set<Class<? extends Command>> supportedCommands() {
        return Set.of(SampleCommand.class);
    }

    @Override
    public Result execute(SampleCommand command) {
        return Result.ok(HANDLED);
    }
}
