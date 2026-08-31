package io.ddd4j.core.cqrs.command;

import java.util.Set;

/** 命令执行器：声明支持的命令类型并完成业务执行。 */
public interface CommandExecutor<C extends Command> {
    Set<Class<? extends Command>> supportedCommands();
    Result<?> execute(C command);
}
