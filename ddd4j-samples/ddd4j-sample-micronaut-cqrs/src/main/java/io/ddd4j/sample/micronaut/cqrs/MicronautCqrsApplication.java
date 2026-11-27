package io.ddd4j.sample.micronaut.cqrs;

import io.micronaut.runtime.Micronaut;

/**
 * 只装配共享四表商品交易和 Micronaut 原生命令总线。
 */
public final class MicronautCqrsApplication {
    /**
     * 限制调用方直接创建 <code>MicronautCqrsApplication</code> 实例。
     */
    private MicronautCqrsApplication() {
    }

    /**
     * 启动 Micronaut 商品交易 CQRS 示例。
     *
     * @param args 应用启动参数
     */
    public static void main(String[] args) {
        Micronaut.run(MicronautCqrsApplication.class, args);
    }
}
