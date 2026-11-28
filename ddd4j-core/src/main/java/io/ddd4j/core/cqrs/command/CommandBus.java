package io.ddd4j.core.cqrs.command;

import io.ddd4j.core.api.R;

/**
 * 框架无关的 CQRS 命令总线。
 */
public interface CommandBus {

    /**
     * \u5c06\u547d\u4ee4\u4ea4\u7ed9\u5339\u914d\u7684\u6267\u884c\u5668\u5904\u7406\u3002
     * @param <T> \u547d\u4ee4\u7ed3\u679c\u6570\u636e\u7c7b\u578b
     * @param command \u5f85\u6267\u884c\u547d\u4ee4
     * @return \u547d\u4ee4\u6267\u884c\u7ed3\u679c
     */
    <T> R<T> execute(Command command);
}
