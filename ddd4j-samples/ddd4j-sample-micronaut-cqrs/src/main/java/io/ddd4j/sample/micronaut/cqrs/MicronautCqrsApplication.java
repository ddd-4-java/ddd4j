package io.ddd4j.sample.micronaut.cqrs;

import io.micronaut.runtime.Micronaut;

/**
 * 只装配共享四表商品交易和 Micronaut 原生命令总线。
 */
public final class MicronautCqrsApplication {
    private MicronautCqrsApplication() {
    }

    public static void main(String[] args) {
        Micronaut.run(MicronautCqrsApplication.class, args);
    }
}
