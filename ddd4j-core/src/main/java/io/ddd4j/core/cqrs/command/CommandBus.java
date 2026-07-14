package io.ddd4j.core.cqrs.command;

/**
 * 框架无关的 CQRS 命令总线。
 */
public interface CommandBus {

    <R> Result<R> execute(Command command);
}
