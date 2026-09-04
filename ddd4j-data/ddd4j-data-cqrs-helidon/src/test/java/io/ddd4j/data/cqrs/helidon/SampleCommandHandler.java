package io.ddd4j.data.cqrs.helidon;

import java.util.Collections;
import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.data.cqrs.CommandHandler;

import java.util.Set;

/**
 * 样例命令处理器（集成测试专用）：模拟集成方业务中的执行器——
 * 经 {@code META-INF/services/io.ddd4j.core.cqrs.command.CommandExecutor}
 * 注册（ServiceLoader 发现键，见 {@code HelidonCommandBus} javadoc 的发现注记）
 * ＋{@code @CommandHandler} 发现注解（声明性元数据）。
 *
 * <p>注意：本类必须 {@code public} 且带公有构造（ServiceLoader 的实例化契约，
 * 与 -spring/-quarkus/-micronaut 模块样例的包私有写法不同——那三者的容器
 * 发现机制不要求 public）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@CommandHandler(SampleCommand.class)
public class SampleCommandHandler implements CommandExecutor<SampleCommand> {

    /**
     * execute 返回的载荷标记（IT 断言「真实路由到本 Handler」用）。
     */
    static final String HANDLED = "handled-by-helidon-handler";

    @Override
    public Set<Class<? extends Command>> supportedCommands() {
        return Collections.singleton(SampleCommand.class);
    }

    @Override
    public Result execute(SampleCommand command) {
        return Result.ok(HANDLED);
    }
}
