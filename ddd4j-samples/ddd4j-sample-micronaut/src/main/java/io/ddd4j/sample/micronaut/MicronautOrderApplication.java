package io.ddd4j.sample.micronaut;

import io.micronaut.runtime.Micronaut;

/**
 * 共享 Order 业务内核的 Micronaut 启动入口。
 */
public final class MicronautOrderApplication {

    private MicronautOrderApplication() {
    }

    public static void main(String[] args) {
        Micronaut.run(MicronautOrderApplication.class, args);
    }
}
